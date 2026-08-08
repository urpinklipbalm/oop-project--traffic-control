package com.trafficcontrol.gui;

import com.trafficcontrol.engine.SimulationEngine;
import com.trafficcontrol.exceptions.InvalidRouteException;
import com.trafficcontrol.exceptions.SimulationStateException;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficObserver;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * the dashboard window: Start/Stop controls, the live animated city map,
 * and a scrolling event log underneath it.
 *
 * still to come (see docs/TODO_Nameer.md): a ControlPanel for the speed
 * slider and the save/load/export buttons, and a StatisticsPanel showing
 * live throughput and wait times. both slot in around the map without
 * changing anything here beyond the layout call.
 */
public class MainFrame extends JFrame implements TrafficObserver {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SimulationEngine engine;
    private final CityPanel cityPanel;
    private final JTextArea eventLog = new JTextArea();
    private final JButton startButton = new JButton("Start Simulation");
    private final JButton stopButton = new JButton("Stop Simulation");
    private final JButton spawnCarButton = new JButton("Spawn Custom Car");
    private final JLabel statusLabel = new JLabel("Stopped");
    private int nextManualCarNumber = 1;

    public MainFrame(SimulationEngine engine) {
        super("Smart City Traffic Control - Simulation Dashboard");
        this.engine = engine;
        this.cityPanel = new CityPanel(engine);

        // the map subscribes itself so callers only ever have to register the frame
        engine.addObserver(cityPanel);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAndExit();
            }
        });

        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildSplitView(), BorderLayout.CENTER);

        setSize(1100, 780);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
    }

    /** map on top, event log below, draggable divider between them. */
    private JComponent buildSplitView() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cityPanel, buildLogPanel());
        split.setResizeWeight(1.0); // extra height from resizing goes to the map, not the log
        split.setDividerLocation(540);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));

        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        spawnCarButton.addActionListener(e -> spawnCar());
        stopButton.setEnabled(false);
        spawnCarButton.setEnabled(false);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        toolbar.add(startButton);
        toolbar.add(stopButton);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(new JLabel("Status:"));
        toolbar.add(statusLabel);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(spawnCarButton);
        return toolbar;
    }

    private JComponent buildLogPanel() {
        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventLog.setLineWrap(true);

        JScrollPane scrollPane = new JScrollPane(eventLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Live Simulation Events"));
        scrollPane.setPreferredSize(new Dimension(0, 180));
        return scrollPane;
    }

    private void startSimulation() {
        try {
            engine.start();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            spawnCarButton.setEnabled(true);
            statusLabel.setText("Running");
        } catch (SimulationStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot start", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void stopSimulation() {
        try {
            engine.stop();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            spawnCarButton.setEnabled(false);
            statusLabel.setText("Stopped");
        } catch (SimulationStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot stop", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void spawnCar() {
        String[] intersectionIds = engine.getCityMap().getIntersectionIds().toArray(String[]::new);
        if (intersectionIds.length < 2) {
            JOptionPane.showMessageDialog(this, "The map needs at least two intersections.",
                    "Cannot spawn car", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<String> originBox = new JComboBox<>(intersectionIds);
        JComboBox<String> destinationBox = new JComboBox<>(intersectionIds);
        destinationBox.setSelectedIndex(1);
        JTextField labelField = new JTextField("Manual Car " + nextManualCarNumber, 18);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Start intersection:"));
        form.add(originBox);
        form.add(new JLabel("Destination:"));
        form.add(destinationBox);
        form.add(new JLabel("Tracking label:"));
        form.add(labelField);

        while (JOptionPane.showConfirmDialog(this, form, "Spawn Custom Car",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                engine.spawnCar((String) originBox.getSelectedItem(),
                        (String) destinationBox.getSelectedItem(), labelField.getText());
                nextManualCarNumber++;
                return;
            } catch (SimulationStateException | InvalidRouteException | IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot spawn car", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void shutdownAndExit() {
        if (engine.isRunning()) {
            engine.stop();
        }
        dispose();
        System.exit(0);
    }

    // --- TrafficObserver: these fire from background simulation threads, so every ui touch hops to the EDT ---

    @Override
    public void onVehicleSpawned(Vehicle vehicle) {
        String label = vehicle.getCustomLabel() == null ? "" : " [" + vehicle.getCustomLabel() + "]";
        appendLine(vehicle.getTypeName() + " " + vehicle.getId() + label + " spawned ("
                + vehicle.getOriginIntersection().getId() + " -> " + vehicle.getDestinationIntersection().getId() + ")");
    }

    @Override
    public void onVehicleArrived(Vehicle vehicle, long waitTimeMillis, long travelTimeMillis) {
        String label = vehicle.getCustomLabel() == null ? "" : " [" + vehicle.getCustomLabel() + "]";
        appendLine(vehicle.getTypeName() + " " + vehicle.getId() + label
                + " arrived - waited " + waitTimeMillis + "ms");
    }

    @Override
    public void onLightPhaseChanged(String intersectionId, LightPhase phase) {
        // deliberately not logged - 9 intersections cycling every few seconds would drown
        // out everything else. the map already shows every phase change as it happens.
    }

    @Override
    public void onPreemption(String intersectionId, Direction direction) {
        appendLine("EMERGENCY preemption at " + intersectionId + " for a vehicle from " + direction);
    }

    @Override
    public void onSimulationMessage(String message) {
        appendLine(message);
    }

    private void appendLine(String text) {
        String line = "[" + TIME_FORMAT.format(LocalTime.now()) + "] " + text + "\n";
        SwingUtilities.invokeLater(() -> {
            eventLog.append(line);
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
    }
}
