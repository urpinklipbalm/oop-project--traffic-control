package com.trafficcontrol;

import com.formdev.flatlaf.FlatLightLaf;
import com.trafficcontrol.engine.SimulationEngine;
import com.trafficcontrol.gui.MainFrame;
import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.persistence.DefaultCityMapFactory;
import com.trafficcontrol.persistence.EventLogger;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;

/**
 * entry point. wires everything together: build a city map, create the
 * simulation engine, attach a file-based event logger and the gui to it
 * as observers, then hand control to swing.
 *
 * see docs/ARCHITECTURE.md for how the pieces fit together, and
 * docs/TODO_Ayesha.md / docs/TODO_Nameer.md for what still needs to
 * replace the two TODO-marked lines below.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();

        // TODO(Ayesha): once persistence.PersistenceService has a real CSV loader, load
        // src/main/resources/maps/default-city.csv through it instead of this hardcoded grid.
        CityMap cityMap = DefaultCityMapFactory.createDefaultGrid();
        SimulationEngine engine = new SimulationEngine(cityMap);

        attachEventLogger(engine);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (engine.isRunning()) {
                engine.stop();
            }
        }, "shutdown-hook"));

        // TODO(Nameer): once gui.MainFrame grows into the full dashboard (CityPanel +
        // ControlPanel + StatisticsPanel), this is still the only line Main needs to change.
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(engine);
            engine.addObserver(frame);
            frame.setVisible(true);
        });
    }

    private static void attachEventLogger(SimulationEngine engine) {
        try {
            EventLogger eventLogger = new EventLogger(Path.of("logs"));
            engine.addObserver(eventLogger);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    eventLogger.close();
                } catch (IOException ignored) {
                    // best-effort flush on the way out - nothing useful to do if this fails
                }
            }, "event-logger-shutdown"));
        } catch (IOException e) {
            // file logging is a nice-to-have, not something that should stop the app from launching
            System.err.println("could not open the event log file, continuing without file logging: " + e.getMessage());
        }
    }
}
