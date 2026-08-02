package com.trafficcontrol.engine;

import com.trafficcontrol.exceptions.SimulationStateException;
import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficEventPublisher;
import com.trafficcontrol.observer.TrafficObserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * owns the whole running simulation: the thread pool, the three
 * long-lived worker threads (spawner/mover/adaptive controller), one
 * TrafficLight thread per intersection, and the observer list everything
 * reports events through.
 *
 * this is the class the gui talks to - it never touches model classes'
 * internals directly, only start()/stop()/addObserver()/getStatistics().
 */
public class SimulationEngine implements TrafficEventPublisher {

    private final CityMap cityMap;
    private final SimulationStatistics statistics = new SimulationStatistics();
    private final SimulationClock clock = new SimulationClock();
    private final List<TrafficObserver> observers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService executor;
    private VehicleSpawner spawner;
    private VehicleMover mover;
    private AdaptiveSignalController adaptiveController;

    public SimulationEngine(CityMap cityMap) {
        this.cityMap = cityMap;
    }

    public void addObserver(TrafficObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(TrafficObserver observer) {
        observers.remove(observer);
    }

    public SimulationStatistics getStatistics() {
        return statistics;
    }

    /** exposed so the gui's speed slider can retune the simulation while it runs. */
    public SimulationClock getClock() {
        return clock;
    }

    public CityMap getCityMap() {
        return cityMap;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * starts every worker thread. compareAndSet guards against a double
     * start racing in from two different callers (e.g. an eager double
     * click on the gui's Start button before it's had a chance to
     * disable itself).
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new SimulationStateException("simulation is already running");
        }

        int intersectionCount = cityMap.getIntersections().size();
        executor = Executors.newFixedThreadPool(intersectionCount + 3, new DaemonThreadFactory());

        for (Intersection intersection : cityMap.getIntersections()) {
            intersection.getTrafficLight().setPublisher(this);
            submitNamed(intersection.getTrafficLight(), "traffic-light-" + intersection.getId());
        }

        spawner = new VehicleSpawner(cityMap, this, statistics);
        mover = new VehicleMover(cityMap, this, statistics, clock);
        adaptiveController = new AdaptiveSignalController(cityMap, this);

        submitNamed(spawner, "vehicle-spawner");
        submitNamed(mover, "vehicle-mover");
        submitNamed(adaptiveController, "adaptive-signal-controller");

        publishMessage("simulation started with " + intersectionCount + " intersections");
    }

    /** signals every worker to stop, then waits (briefly) for a clean shutdown before forcing it. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            throw new SimulationStateException("simulation is not running");
        }

        for (Intersection intersection : cityMap.getIntersections()) {
            intersection.getTrafficLight().stop();
        }
        spawner.stop();
        mover.stop();
        adaptiveController.stop();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        publishMessage("simulation stopped - " + statistics);
    }

    private void submitNamed(Runnable task, String threadName) {
        executor.submit(() -> {
            Thread.currentThread().setName(threadName);
            task.run();
        });
    }

    // --- TrafficEventPublisher: fan every event out to all registered observers ---

    @Override
    public void publishVehicleSpawned(Vehicle vehicle) {
        for (TrafficObserver observer : observers) {
            observer.onVehicleSpawned(vehicle);
        }
    }

    @Override
    public void publishVehicleArrived(Vehicle vehicle, long waitTimeMillis, long travelTimeMillis) {
        for (TrafficObserver observer : observers) {
            observer.onVehicleArrived(vehicle, waitTimeMillis, travelTimeMillis);
        }
    }

    @Override
    public void publishLightPhaseChanged(String intersectionId, LightPhase phase) {
        for (TrafficObserver observer : observers) {
            observer.onLightPhaseChanged(intersectionId, phase);
        }
    }

    @Override
    public void publishPreemption(String intersectionId, Direction direction) {
        for (TrafficObserver observer : observers) {
            observer.onPreemption(intersectionId, direction);
        }
    }

    @Override
    public void publishMessage(String message) {
        for (TrafficObserver observer : observers) {
            observer.onSimulationMessage(message);
        }
    }

    /** worker threads are daemons so a crashed gui can't leave the jvm hanging open. */
    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setDaemon(true);
            return thread;
        }
    }
}
