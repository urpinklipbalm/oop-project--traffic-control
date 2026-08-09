package com.trafficcontrol.gui;

import com.trafficcontrol.engine.SimulationEngine;
import com.trafficcontrol.engine.SimulationEngine.TrafficVolume;
import com.trafficcontrol.exceptions.InvalidRouteException;
import com.trafficcontrol.exceptions.SimulationStateException;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficObserver;
import com.trafficcontrol.persistence.CsvCityMapLoader;
import com.trafficcontrol.persistence.SimulationSnapshot;
import com.trafficcontrol.exceptions.CityMapLoadException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

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

    // Add any new city-map CSV to this directory; the Load City menu discovers it automatically.
    private static final Path CITY_MAP_DIRECTORY = Path.of("src/main/resources/maps");

    private SimulationEngine engine;
    private CityPanel cityPanel;
    private final TrafficObserver persistentObserver;
    private final CsvCityMapLoader persistence = new CsvCityMapLoader();
    private final JPanel mapContainer = new JPanel(new BorderLayout());
    private final JTextArea eventLog = new JTextArea();
    private final JButton startButton = new JButton("Start Simulation");
    private final JButton stopButton = new JButton("Stop Simulation");
    private final JButton spawnCarButton = new JButton("Spawn Custom Car");
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JComboBox<String> vehicleSpeedBox = new JComboBox<>(
            new String[]{"1x", "2x", "4x", "8x", "12x", "20x"});
    private final JComboBox<TrafficVolume> trafficVolumeBox = new JComboBox<>(TrafficVolume.values());
    private int nextManualCarNumber = 1;

    public MainFrame(SimulationEngine engine) {
        this(engine, null);
    }

    public MainFrame(SimulationEngine engine, TrafficObserver persistentObserver) {
        super("Smart City Traffic Control - Simulation Dashboard");
        this.engine = engine;
        this.persistentObserver = persistentObserver;
        this.cityPanel = new CityPanel(engine);
        vehicleSpeedBox.setSelectedItem(formatSpeed(engine.getClock().getSpeedFactor()));
        trafficVolumeBox.setSelectedItem(engine.getTrafficVolume());

        attachObservers();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownAndExit();
            }
        });

        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildSplitView(), BorderLayout.CENTER);

        setSize(1100, 780);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
    }

    /** map on top, event log below, draggable divider between them. */
    private JComponent buildSplitView() {
        mapContainer.add(cityPanel, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapContainer, buildLogPanel());
        split.setResizeWeight(1.0); // extra height from resizing goes to the map, not the log
        split.setDividerLocation(540);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(buildCityMenu());
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Load Snapshot...", this::loadSnapshot));
        fileMenu.add(menuItem("Save Snapshot As...", this::saveSnapshot));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Export Statistics...", this::exportStatistics));
        menuBar.add(fileMenu);
        return menuBar;
    }

    private JMenu buildCityMenu() {
        JMenu cityMenu = new JMenu("Load City");
        List<Path> cityFiles;
        try (var files = Files.list(CITY_MAP_DIRECTORY)) {
            cityFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            JMenuItem unavailable = new JMenuItem("Maps directory unavailable");
            unavailable.setEnabled(false);
            cityMenu.add(unavailable);
            return cityMenu;
        }

        for (Path cityFile : cityFiles) {
            cityMenu.add(menuItem(cityDisplayName(cityFile), () -> loadCityMap(cityFile)));
        }
        if (cityFiles.isEmpty()) {
            JMenuItem empty = new JMenuItem("No city maps found");
            empty.setEnabled(false);
            cityMenu.add(empty);
        }
        return cityMenu;
    }

    private String cityDisplayName(Path cityFile) {
        String filename = cityFile.getFileName().toString().replaceFirst("(?i)\\.csv$", "");
        String[] words = filename.split("[-_]");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!displayName.isEmpty()) displayName.append(' ');
                displayName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return displayName.toString();
    }

    private JMenuItem menuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void attachObservers() {
        engine.addObserver(this);
        engine.addObserver(cityPanel);
        if (persistentObserver != null) {
            engine.addObserver(persistentObserver);
        }
    }

    private boolean requireStoppedForLoad() {
        if (!engine.isRunning()) {
            return true;
        }
        JOptionPane.showMessageDialog(this, "Stop the simulation before loading a map or snapshot.",
                "Simulation is running", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private JFileChooser csvChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        return chooser;
    }

    private void loadCityMap(Path cityFile) {
        if (!requireStoppedForLoad()) return;
        try {
            replaceEngine(new SimulationEngine(persistence.loadCityMap(cityFile.toString())));
            appendLine("loaded city map: " + cityDisplayName(cityFile));
        } catch (CityMapLoadException | IllegalArgumentException e) {
            showPersistenceError("Could not load city map", e);
        }
    }

    private void saveSnapshot() {
        if (!requireStoppedForLoad()) return;
        JFileChooser chooser = csvChooser("Save Simulation Snapshot");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = ensureCsvExtension(chooser.getSelectedFile());
        try {
            persistence.saveSnapshot(engine.getCityMap(), engine.getStatistics(), file.getPath());
            appendLine("saved simulation snapshot: " + file.getName());
        } catch (CityMapLoadException e) {
            showPersistenceError("Could not save snapshot", e);
        }
    }

    private void loadSnapshot() {
        if (!requireStoppedForLoad()) return;
        JFileChooser chooser = csvChooser("Load Simulation Snapshot");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            SimulationSnapshot snapshot = persistence.loadSnapshot(chooser.getSelectedFile().getPath());
            SimulationEngine replacement = new SimulationEngine(snapshot.getCityMap());
            replacement.getStatistics().restore(snapshot.getVehiclesSpawned(), snapshot.getVehiclesArrived(),
                    snapshot.getAverageWaitTimeMillis(), snapshot.getAverageTravelTimeMillis());
            replacement.restoreVehicles(snapshot.getUnfinishedVehicles());
            replaceEngine(replacement);
            appendLine("loaded snapshot: " + chooser.getSelectedFile().getName()
                    + " (" + snapshot.getUnfinishedVehicles().size()
                    + " unfinished vehicles will restart from their origins)");
        } catch (CityMapLoadException | IllegalArgumentException e) {
            showPersistenceError("Could not load snapshot", e);
        }
    }

    private void exportStatistics() {
        JFileChooser chooser = csvChooser("Export Simulation Statistics");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = ensureCsvExtension(chooser.getSelectedFile());
        try {
            persistence.exportStatistics(engine.getStatistics(), file.getPath());
            appendLine("exported statistics: " + file.getName());
        } catch (CityMapLoadException e) {
            showPersistenceError("Could not export statistics", e);
        }
    }

    private void replaceEngine(SimulationEngine replacement) {
        String selectedSpeed = (String) vehicleSpeedBox.getSelectedItem();
        TrafficVolume selectedVolume = (TrafficVolume) trafficVolumeBox.getSelectedItem();
        engine.removeObserver(this);
        engine.removeObserver(cityPanel);
        if (persistentObserver != null) engine.removeObserver(persistentObserver);
        cityPanel.disposePanel();
        mapContainer.remove(cityPanel);

        engine = replacement;
        if (selectedSpeed != null) {
            engine.getClock().setSpeedFactor(parseSpeed(selectedSpeed));
        }
        if (selectedVolume != null) engine.setTrafficVolume(selectedVolume);
        cityPanel = new CityPanel(engine);
        attachObservers();
        mapContainer.add(cityPanel, BorderLayout.CENTER);
        mapContainer.revalidate();
        mapContainer.repaint();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        spawnCarButton.setEnabled(false);
        statusLabel.setText("Stopped");
    }

    private File ensureCsvExtension(File file) {
        return file.getName().toLowerCase().endsWith(".csv")
                ? file : new File(file.getParentFile(), file.getName() + ".csv");
    }

    private void showPersistenceError(String title, Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
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
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(new JLabel("Vehicle Speed:"));
        vehicleSpeedBox.setToolTipText(
                "Changes road travel speed only; traffic lights and spawn frequency stay unchanged.");
        vehicleSpeedBox.addActionListener(e -> applyVehicleSpeed());
        toolbar.add(vehicleSpeedBox);
        toolbar.add(new JLabel("Traffic Volume:"));
        trafficVolumeBox.setToolTipText(
                "Changes automatic spawn frequency only; vehicle road speed stays unchanged.");
        trafficVolumeBox.addActionListener(e -> applyTrafficVolume());
        toolbar.add(trafficVolumeBox);
        return toolbar;
    }

    private void applyVehicleSpeed() {
        String selected = (String) vehicleSpeedBox.getSelectedItem();
        if (selected == null) return;
        engine.getClock().setSpeedFactor(parseSpeed(selected));
        appendLine("vehicle speed changed to " + selected
                + " (lights and spawn frequency unchanged)");
    }

    private void applyTrafficVolume() {
        TrafficVolume volume = (TrafficVolume) trafficVolumeBox.getSelectedItem();
        if (volume == null) return;
        engine.setTrafficVolume(volume);
        appendLine("traffic volume changed to " + volume + " (vehicle speed unchanged)");
    }

    private double parseSpeed(String displayedSpeed) {
        return Double.parseDouble(displayedSpeed.substring(0, displayedSpeed.length() - 1));
    }

    private String formatSpeed(double factor) {
        return Math.round(factor) + "x";
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
    public void onVehicleRestored(Vehicle vehicle) {
        String label = vehicle.getCustomLabel() == null ? "" : " [" + vehicle.getCustomLabel() + "]";
        appendLine(vehicle.getTypeName() + " " + vehicle.getId() + label
                + " restored from snapshot at " + vehicle.getOriginIntersection().getId()
                + " (destination " + vehicle.getDestinationIntersection().getId() + ")");
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
