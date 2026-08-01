package com.trafficcontrol.persistence;

import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficObserver;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * writes every simulation event to a plain-text log file under logs/,
 * one line per event. this is the "file handling" piece that ships with
 * the engine itself, kept deliberately simple (append-only, human
 * readable) - the CSV city-map loader/saver, snapshot save-resume, and
 * statistics export are Ayesha's bigger persistence.PersistenceService
 * work (see docs/TODO_Ayesha.md).
 *
 * several engine threads (every TrafficLight, the mover, the spawner)
 * call these onXxx callbacks concurrently, so every write funnels
 * through a synchronized method guarding the single BufferedWriter -
 * without that, interleaved writes from different threads could corrupt
 * a line halfway through being written.
 */
public class EventLogger implements TrafficObserver, AutoCloseable {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final BufferedWriter writer;

    public EventLogger(Path logDirectory) throws IOException {
        Files.createDirectories(logDirectory);
        String fileName = "simulation-" + FILE_NAME_FORMAT.format(LocalDateTime.now()) + ".log";
        this.writer = Files.newBufferedWriter(logDirectory.resolve(fileName));
    }

    @Override
    public void onVehicleSpawned(Vehicle vehicle) {
        writeLine(vehicle.getTypeName() + " " + vehicle.getId() + " spawned: "
                + vehicle.getOriginIntersection().getId() + " -> " + vehicle.getDestinationIntersection().getId());
    }

    @Override
    public void onVehicleArrived(Vehicle vehicle, long waitTimeMillis, long travelTimeMillis) {
        writeLine(vehicle.getTypeName() + " " + vehicle.getId() + " arrived: waited "
                + waitTimeMillis + "ms of a " + travelTimeMillis + "ms trip");
    }

    @Override
    public void onLightPhaseChanged(String intersectionId, LightPhase phase) {
        writeLine("intersection " + intersectionId + " light -> " + phase);
    }

    @Override
    public void onPreemption(String intersectionId, Direction direction) {
        writeLine("intersection " + intersectionId + " PREEMPTED for an emergency vehicle from " + direction);
    }

    @Override
    public void onSimulationMessage(String message) {
        writeLine(message);
    }

    private synchronized void writeLine(String message) {
        try {
            writer.write("[" + TIMESTAMP_FORMAT.format(LocalDateTime.now()) + "] " + message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            // a logging failure should never take the simulation down with it
            System.err.println("event logger failed to write a line: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
