package com.trafficcontrol.gui;

import com.trafficcontrol.engine.SimulationEngine;
import com.trafficcontrol.exceptions.SimulationStateException;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficObserver;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * PLACEHOLDER dashboard window: Start/Stop controls plus a live scrolling
 * event log. its job is to prove the whole app runs end-to-end (engine
 * running -> gui reacting) right now.
 *
 * Nameer's job (see docs/TODO_Nameer.md) is to replace the log panel
 * below with a real CityPanel (a painted map with animated vehicles and
 * light indicators), add a ControlPanel and StatisticsPanel, and apply
 * proper styling - this class is the wiring/starting point, not the
 * finished dashboard.
 */
public class MainFrame extends JFrame implements TrafficObserver {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SimulationEngine engine;
    private final JTextArea eventLog = new JTextArea();
    private final JButton startButton = new JButton("Start Simulation");
    private final JButton stopButton = new JButton("Stop Simulation");
    private final JLabel statusLabel = new JLabel("Stopped");

    public MainFrame(SimulationEngine engine) {
        super("Smart City Traffic Control - Simulation Dashboard");
        this.engine = engine;

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAndExit();
            }
        });

        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildLogPanel(), BorderLayout.CENTER);

        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));

        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        stopButton.setEnabled(false);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        toolbar.add(startButton);
        toolbar.add(stopButton);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(new JLabel("Status:"));
        toolbar.add(statusLabel);
        return toolbar;
    }

    private JComponent buildLogPanel() {
        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventLog.setLineWrap(true);

        JScrollPane scrollPane = new JScrollPane(eventLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Live Simulation Events"));
        return scrollPane;
    }

    private void startSimulation() {
        try {
            engine.start();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
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
            statusLabel.setText("Stopped");
        } catch (SimulationStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot stop", JOptionPane.WARNING_MESSAGE);
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
        appendLine(vehicle.getTypeName() + " " + vehicle.getId() + " spawned ("
                + vehicle.getOriginIntersection().getId() + " -> " + vehicle.getDestinationIntersection().getId() + ")");
    }

    @Override
    public void onVehicleArrived(Vehicle vehicle, long waitTimeMillis, long travelTimeMillis) {
        appendLine(vehicle.getTypeName() + " " + vehicle.getId() + " arrived - waited " + waitTimeMillis + "ms");
    }

    @Override
    public void onLightPhaseChanged(String intersectionId, LightPhase phase) {
        // too noisy for a plain text log across 9 intersections cycling every few seconds - the
        // real CityPanel (Nameer's work) is where phase changes should actually be visualized.
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
