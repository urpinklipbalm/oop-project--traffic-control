package com.trafficcontrol.gui;

import com.trafficcontrol.engine.SimulationEngine;
import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Position;
import com.trafficcontrol.model.Road;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficObserver;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * the live map: roads, moving vehicles, traffic lights and the queues
 * building up behind them.
 *
 * threading - everything here paints on the swing event dispatch thread,
 * driven by a repaint timer, and only ever *reads* simulation state. all
 * of those reads are already safe to make while the engine threads are
 * writing: a road's vehicle list is copy-on-write, a light's phase is
 * volatile, a queue length is taken under the intersection's own lock,
 * and a vehicle's position is published as one immutable snapshot (see
 * Vehicle.RoadPlacement). the panel never writes anything back, so it
 * can't disturb the simulation no matter when a frame lands.
 */
public class CityPanel extends JPanel implements TrafficObserver {

    private static final int FRAME_DELAY_MILLIS = 33; // ~30 fps, smooth enough without burning cpu
    private static final int MARGIN_PIXELS = 48;

    // a two-way street is two Road objects with identical endpoints, so each
    // carriageway is nudged sideways by this much to keep both visible.
    private static final double LANE_OFFSET_PIXELS = 7;

    private static final double CARRIAGEWAY_WIDTH = 12;
    private static final double JUNCTION_SIZE = 34;
    private static final double VEHICLE_LENGTH = 13;
    private static final double VEHICLE_WIDTH = 8;
    private static final double STOP_BAR_LENGTH = 12;
    private static final int MAX_QUEUE_BOXES_DRAWN = 6;
    private static final long PREEMPTION_FLASH_MILLIS = 1500;

    private static final Color BACKGROUND = new Color(0xEEF1F5);
    private static final Color ROAD = new Color(0x3F4855);
    private static final Color CENTRE_LINE = new Color(0xE8EDF2);
    private static final Color JUNCTION = new Color(0x232B36);
    private static final Color LIGHT_GREEN = new Color(0x22C55E);
    private static final Color LIGHT_AMBER = new Color(0xFACC15);
    private static final Color LIGHT_RED = new Color(0xDC2626);
    private static final Color QUEUED_VEHICLE = new Color(0x9CA3AF);
    private static final Color LABEL = new Color(0x4B5563);
    private static final Color CARD_FILL = new Color(0xFFFFFF);
    private static final Color CARD_BORDER = new Color(0xD1D5DB);
    private static final Color PREEMPTION_FLASH = new Color(0xEF4444);

    private final SimulationEngine engine;
    private final CityMap cityMap;

    /**
     * which road arrives at a given intersection from a given direction.
     * queues are keyed by arrival direction, so this is what lets a queue be
     * drawn backing up along the correct street. built once - the map doesn't
     * change while the simulation runs.
     */
    private final Map<Intersection, Map<Direction, Road>> incomingRoads = new HashMap<>();

    /** intersection id -> when it was last preempted, for the fading highlight. */
    private final Map<String, Long> lastPreemption = new ConcurrentHashMap<>();

    // recomputed whenever the panel is resized
    private double scale = 1;
    private double offsetX;
    private double offsetY;

    public CityPanel(SimulationEngine engine) {
        this.engine = engine;
        this.cityMap = engine.getCityMap();
        setBackground(BACKGROUND);
        indexIncomingRoads();
        new Timer(FRAME_DELAY_MILLIS, e -> repaint()).start();
    }

    private void indexIncomingRoads() {
        for (Road road : cityMap.getRoads()) {
            incomingRoads
                    .computeIfAbsent(road.getTo(), key -> new HashMap<>())
                    .put(road.getApproachDirection(), road);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        recomputeTransform();

        long now = System.currentTimeMillis();
        double speedFactor = engine.getClock().getSpeedFactor();

        drawRoads(g2);
        drawQueues(g2);
        drawVehicles(g2, now, speedFactor);
        drawIntersections(g2, now);
        drawLegend(g2);

        g2.dispose();
    }

    /**
     * fits the city into the panel. bounds come from the intersections
     * themselves rather than any assumed grid size, so a different map loaded
     * from file still lays out correctly.
     */
    private void recomputeTransform() {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Intersection intersection : cityMap.getIntersections()) {
            Position position = intersection.getPosition();
            minX = Math.min(minX, position.getX());
            minY = Math.min(minY, position.getY());
            maxX = Math.max(maxX, position.getX());
            maxY = Math.max(maxY, position.getY());
        }

        double cityWidth = Math.max(1, maxX - minX);
        double cityHeight = Math.max(1, maxY - minY);
        double usableWidth = Math.max(1, getWidth() - 2.0 * MARGIN_PIXELS);
        double usableHeight = Math.max(1, getHeight() - 2.0 * MARGIN_PIXELS);

        // one scale for both axes so the city keeps its shape instead of stretching
        scale = Math.min(usableWidth / cityWidth, usableHeight / cityHeight);

        // centre whatever space is left over after fitting
        offsetX = (getWidth() - cityWidth * scale) / 2 - minX * scale;
        offsetY = (getHeight() - cityHeight * scale) / 2 - minY * scale;
    }

    private Point2D toScreen(Position position) {
        return new Point2D.Double(position.getX() * scale + offsetX, position.getY() * scale + offsetY);
    }

    // --- roads ---

    private void drawRoads(Graphics2D g2) {
        // asphalt first: one band per carriageway, so a two-way street reads as
        // two lanes side by side rather than a single fat line
        g2.setColor(ROAD);
        g2.setStroke(new BasicStroke((float) CARRIAGEWAY_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (Road road : cityMap.getRoads()) {
            g2.draw(new Line2D.Double(
                    offsetForCarriageway(road, toScreen(road.getFrom().getPosition())),
                    offsetForCarriageway(road, toScreen(road.getTo().getPosition()))));
        }

        // then the dashed lane divider down the true centre of the street. drawn
        // once per direction, which paints the same dashes twice - harmless, and
        // cheaper than working out which roads pair up.
        g2.setColor(CENTRE_LINE);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                1f, new float[]{9, 9}, 0));
        for (Road road : cityMap.getRoads()) {
            g2.draw(new Line2D.Double(
                    toScreen(road.getFrom().getPosition()),
                    toScreen(road.getTo().getPosition())));
        }
    }

    /**
     * nudges a road sideways so the two directions of the same street sit next
     * to each other rather than on top of each other - which also means each
     * vehicle visibly drives on its own side.
     */
    private Point2D offsetForCarriageway(Road road, Point2D point) {
        Point2D start = toScreen(road.getFrom().getPosition());
        Point2D end = toScreen(road.getTo().getPosition());
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double length = Math.hypot(dx, dy);
        if (length == 0) {
            return point;
        }
        // perpendicular to travel, consistently on one side => right-hand traffic
        double perpX = -dy / length;
        double perpY = dx / length;
        return new Point2D.Double(
                point.getX() + perpX * LANE_OFFSET_PIXELS,
                point.getY() + perpY * LANE_OFFSET_PIXELS);
    }

    // --- vehicles ---

    private void drawVehicles(Graphics2D g2, long now, double speedFactor) {
        for (Road road : cityMap.getRoads()) {
            Point2D from = offsetForCarriageway(road, toScreen(road.getFrom().getPosition()));
            Point2D to = offsetForCarriageway(road, toScreen(road.getTo().getPosition()));

            // safe to iterate while the mover mutates it - the list is copy-on-write
            for (Vehicle vehicle : road.getVehiclesOnRoad()) {
                double progress = vehicle.getProgressAlongRoad(now, speedFactor);
                double x = from.getX() + (to.getX() - from.getX()) * progress;
                double y = from.getY() + (to.getY() - from.getY()) * progress;
                double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
                drawVehicle(g2, x, y, angle, vehicle.getColor());
            }
        }
    }

    /** a small rounded box pointing the way it's travelling. */
    private void drawVehicle(Graphics2D g2, double x, double y, double angle, Color color) {
        Graphics2D shape = (Graphics2D) g2.create();
        shape.translate(x, y);
        shape.rotate(angle);

        shape.setColor(color);
        shape.fill(new RoundRectangle2D.Double(
                -VEHICLE_LENGTH / 2, -VEHICLE_WIDTH / 2, VEHICLE_LENGTH, VEHICLE_WIDTH, 3, 3));

        // thin dark outline keeps vehicles legible against the dark road
        shape.setColor(new Color(0, 0, 0, 70));
        shape.setStroke(new BasicStroke(1));
        shape.draw(new RoundRectangle2D.Double(
                -VEHICLE_LENGTH / 2, -VEHICLE_WIDTH / 2, VEHICLE_LENGTH, VEHICLE_WIDTH, 3, 3));

        shape.dispose();
    }

    // --- queues waiting at red lights ---

    private void drawQueues(Graphics2D g2) {
        for (Intersection intersection : cityMap.getIntersections()) {
            Map<Direction, Road> approaches = incomingRoads.getOrDefault(intersection, Map.of());
            for (Map.Entry<Direction, Road> entry : approaches.entrySet()) {
                int queued = intersection.getQueueLength(entry.getKey());
                if (queued > 0) {
                    drawQueue(g2, entry.getValue(), queued);
                }
            }
        }
    }

    /** stacks waiting vehicles back from the junction along the road they arrived on. */
    private void drawQueue(Graphics2D g2, Road road, int queued) {
        Point2D from = offsetForCarriageway(road, toScreen(road.getFrom().getPosition()));
        Point2D to = offsetForCarriageway(road, toScreen(road.getTo().getPosition()));

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double length = Math.hypot(dx, dy);
        if (length == 0) {
            return;
        }
        double stepX = -dx / length * (VEHICLE_LENGTH + 3);
        double stepY = -dy / length * (VEHICLE_LENGTH + 3);
        double angle = Math.atan2(dy, dx);

        // first box sits just outside the junction, the rest tail back behind it
        double startX = to.getX() - dx / length * (JUNCTION_SIZE / 2 + VEHICLE_LENGTH);
        double startY = to.getY() - dy / length * (JUNCTION_SIZE / 2 + VEHICLE_LENGTH);

        int drawn = Math.min(queued, MAX_QUEUE_BOXES_DRAWN);
        for (int i = 0; i < drawn; i++) {
            drawVehicle(g2, startX + stepX * i, startY + stepY * i, angle, QUEUED_VEHICLE);
        }

        if (queued > MAX_QUEUE_BOXES_DRAWN) {
            g2.setColor(LABEL);
            g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
            g2.drawString("+" + (queued - MAX_QUEUE_BOXES_DRAWN),
                    (float) (startX + stepX * drawn), (float) (startY + stepY * drawn));
        }
    }

    // --- intersections and their signals ---

    private void drawIntersections(Graphics2D g2, long now) {
        for (Intersection intersection : cityMap.getIntersections()) {
            Point2D centre = toScreen(intersection.getPosition());
            LightPhase phase = intersection.getTrafficLight().getPhase();

            drawPreemptionFlash(g2, intersection, centre, now);

            g2.setColor(JUNCTION);
            g2.fill(new RoundRectangle2D.Double(
                    centre.getX() - JUNCTION_SIZE / 2, centre.getY() - JUNCTION_SIZE / 2,
                    JUNCTION_SIZE, JUNCTION_SIZE, 7, 7));

            drawStopBars(g2, intersection, centre, phase);
            drawJunctionLabel(g2, intersection.getId(), centre);
        }
    }

    /**
     * a coloured stop line across each approach, sitting on the edge of the
     * junction the traffic actually arrives at. more readable than a single lamp
     * per intersection: you can see at a glance which streets are being held and
     * which are flowing.
     */
    private void drawStopBars(Graphics2D g2, Intersection intersection, Point2D centre, LightPhase phase) {
        Map<Direction, Road> approaches = incomingRoads.getOrDefault(intersection, Map.of());
        for (Map.Entry<Direction, Road> entry : approaches.entrySet()) {
            Road road = entry.getValue();
            Point2D arrival = offsetForCarriageway(road, toScreen(road.getTo().getPosition()));
            Point2D start = offsetForCarriageway(road, toScreen(road.getFrom().getPosition()));

            double dx = arrival.getX() - start.getX();
            double dy = arrival.getY() - start.getY();
            double length = Math.hypot(dx, dy);
            if (length == 0) {
                continue;
            }
            double unitX = dx / length;
            double unitY = dy / length;

            // back off from the junction centre so the bar lands on its edge
            double barX = arrival.getX() - unitX * (JUNCTION_SIZE / 2);
            double barY = arrival.getY() - unitY * (JUNCTION_SIZE / 2);

            g2.setColor(colorFor(phase, entry.getKey()));
            g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(
                    barX - unitY * STOP_BAR_LENGTH / 2, barY + unitX * STOP_BAR_LENGTH / 2,
                    barX + unitY * STOP_BAR_LENGTH / 2, barY - unitX * STOP_BAR_LENGTH / 2));
        }
    }

    /** id on a small white chip so it stays readable wherever it lands. */
    private void drawJunctionLabel(Graphics2D g2, String id, Point2D centre) {
        g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
        int textWidth = g2.getFontMetrics().stringWidth(id);
        double chipWidth = textWidth + 8;
        double chipHeight = 14;
        double chipX = centre.getX() - chipWidth / 2;
        double chipY = centre.getY() + JUNCTION_SIZE / 2 + 6;

        g2.setColor(CARD_FILL);
        g2.fill(new RoundRectangle2D.Double(chipX, chipY, chipWidth, chipHeight, 5, 5));
        g2.setColor(CARD_BORDER);
        g2.setStroke(new BasicStroke(1));
        g2.draw(new RoundRectangle2D.Double(chipX, chipY, chipWidth, chipHeight, 5, 5));

        g2.setColor(LABEL);
        g2.drawString(id, (float) (chipX + 4), (float) (chipY + 11));
    }

    private static Color colorFor(LightPhase phase, Direction approach) {
        if (phase.isGreenFor(approach)) {
            return LIGHT_GREEN;
        }
        return phase.isYellowFor(approach) ? LIGHT_AMBER : LIGHT_RED;
    }

    /** briefly rings a junction that an emergency vehicle just forced a phase change at. */
    private void drawPreemptionFlash(Graphics2D g2, Intersection intersection, Point2D centre, long now) {
        Long at = lastPreemption.get(intersection.getId());
        if (at == null) {
            return;
        }
        long age = now - at;
        if (age > PREEMPTION_FLASH_MILLIS) {
            lastPreemption.remove(intersection.getId());
            return;
        }
        double fade = 1.0 - (double) age / PREEMPTION_FLASH_MILLIS;
        double radius = JUNCTION_SIZE + 14 * (1 - fade); // ring expands as it fades out
        g2.setColor(new Color(PREEMPTION_FLASH.getRed(), PREEMPTION_FLASH.getGreen(),
                PREEMPTION_FLASH.getBlue(), (int) (170 * fade)));
        g2.setStroke(new BasicStroke(3));
        g2.draw(new Ellipse2D.Double(
                centre.getX() - radius, centre.getY() - radius, radius * 2, radius * 2));
    }

    // --- legend ---

    private void drawLegend(Graphics2D g2) {
        List<String> labels = List.of("Car", "Bus", "Motorcycle", "Emergency", "Waiting");
        List<Color> colors = List.of(
                new Color(0x3B82F6), new Color(0xF59E0B), new Color(0x10B981),
                new Color(0xEF4444), new Color(0x9CA3AF));

        int padding = 10;
        int lineHeight = 16;
        int width = 128;
        int height = padding * 2 + lineHeight * labels.size();
        int x = 12;
        int y = 12;

        g2.setColor(CARD_FILL);
        g2.fill(new RoundRectangle2D.Double(x, y, width, height, 8, 8));
        g2.setColor(CARD_BORDER);
        g2.setStroke(new BasicStroke(1));
        g2.draw(new RoundRectangle2D.Double(x, y, width, height, 8, 8));

        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        for (int i = 0; i < labels.size(); i++) {
            int rowY = y + padding + i * lineHeight;
            g2.setColor(colors.get(i));
            g2.fill(new RoundRectangle2D.Double(x + padding, rowY + 3, VEHICLE_LENGTH, VEHICLE_WIDTH, 3, 3));
            g2.setColor(LABEL);
            g2.drawString(labels.get(i), x + padding + 20, rowY + 11);
        }
    }

    // --- TrafficObserver ---

    @Override
    public void onPreemption(String intersectionId, Direction direction) {
        // called from an engine thread; just records a timestamp the paint pass
        // reads later, so there's no ui work happening off the EDT here.
        lastPreemption.put(intersectionId, System.currentTimeMillis());
    }
}
