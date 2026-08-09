package com.trafficcontrol.engine;

import com.trafficcontrol.exceptions.InvalidRouteException;
import com.trafficcontrol.model.Bus;
import com.trafficcontrol.model.Car;
import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.EmergencyVehicle;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.Motorcycle;
import com.trafficcontrol.model.Road;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficEventPublisher;
import com.trafficcontrol.persistence.SimulationSnapshot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * periodically creates a new vehicle with a random origin/destination and
 * drops it onto the first road of its route. runs on its own thread so
 * spawning never blocks (or is blocked by) vehicles already moving.
 */
public class VehicleSpawner implements Runnable {

    // keep automatic traffic sparse enough that individual vehicles and queue
    // movements remain readable. manual spawning is still immediate.
    private static final long MIN_SPAWN_INTERVAL_MILLIS = 900;
    private static final long MAX_SPAWN_INTERVAL_MILLIS = 1600;
    private static final double EMERGENCY_VEHICLE_CHANCE = 0.06;
    private static final double BUS_CHANCE = 0.18;
    private static final double MOTORCYCLE_CHANCE = 0.20;

    private final CityMap cityMap;
    private final TrafficEventPublisher publisher;
    private final SimulationStatistics statistics;
    private volatile boolean running = true;
    private volatile double spawnIntervalMultiplier = 1.0;

    public VehicleSpawner(CityMap cityMap, TrafficEventPublisher publisher, SimulationStatistics statistics) {
        this.cityMap = cityMap;
        this.publisher = publisher;
        this.statistics = statistics;
    }

    @Override
    public void run() {
        List<String> ids = cityMap.getIntersectionIds();
        if (ids.size() < 2) {
            publisher.publishMessage("vehicle spawner stopped: city map needs at least 2 intersections");
            return;
        }
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                spawnOne(ids);
                long baseInterval = ThreadLocalRandom.current()
                        .nextLong(MIN_SPAWN_INTERVAL_MILLIS, MAX_SPAWN_INTERVAL_MILLIS);
                Thread.sleep(Math.max(100, Math.round(baseInterval * spawnIntervalMultiplier)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvalidRouteException e) {
                // the random origin/destination pair had no path between them - just try again next tick
            } catch (Exception e) {
                publisher.publishMessage("vehicle spawner hit an error: " + e.getMessage());
            }
        }
    }

    private void spawnOne(List<String> ids) throws InvalidRouteException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Intersection> route = chooseRandomRoute(ids, random);
        spawnVehicle(createRandomVehicle(route, random), route);
    }

    /**
     * immediately creates one car on a random valid route. this is called by
     * the gui's manual spawn control and is safe to run alongside the normal
     * spawner thread: roads use a copy-on-write vehicle list and statistics are
     * atomic.
     */
    public Vehicle spawnCarNow() throws InvalidRouteException {
        List<String> ids = cityMap.getIntersectionIds();
        if (ids.size() < 2) {
            throw new InvalidRouteException("city map needs at least 2 intersections");
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Intersection> route = chooseRandomRoute(ids, random);
        return spawnVehicle(new Car(route), route);
    }

    /** creates a labeled car on the exact route selected in the gui. */
    public Vehicle spawnCarNow(String originId, String destinationId, String customLabel)
            throws InvalidRouteException {
        if (customLabel == null || customLabel.isBlank()) {
            throw new IllegalArgumentException("enter a label for the car");
        }
        if (customLabel.trim().length() > 24) {
            throw new IllegalArgumentException("car labels can be at most 24 characters");
        }

        List<Intersection> route = cityMap.getRoute(originId, destinationId);
        return spawnVehicle(new Car(route, customLabel), route);
    }

    /** Recreates one unfinished snapshot vehicle at its original intersection. */
    public Vehicle restoreVehicle(SimulationSnapshot.SavedVehicle saved) throws InvalidRouteException {
        List<Intersection> route = cityMap.getRoute(saved.originId(), saved.destinationId());
        Vehicle vehicle = switch (saved.type()) {
            case "Car" -> new Car(route, saved.customLabel());
            case "Bus" -> new Bus(route);
            case "Motorcycle" -> new Motorcycle(route);
            case "Emergency" -> new EmergencyVehicle(route);
            default -> throw new IllegalArgumentException("unknown saved vehicle type: " + saved.type());
        };
        return placeVehicle(vehicle, route, false);
    }

    private List<Intersection> chooseRandomRoute(List<String> ids, ThreadLocalRandom random)
            throws InvalidRouteException {
        String originId = ids.get(random.nextInt(ids.size()));
        String destinationId;
        do {
            destinationId = ids.get(random.nextInt(ids.size()));
        } while (destinationId.equals(originId));

        return cityMap.getRoute(originId, destinationId);
    }

    private Vehicle spawnVehicle(Vehicle vehicle, List<Intersection> route) throws InvalidRouteException {
        return placeVehicle(vehicle, route, true);
    }

    private Vehicle placeVehicle(Vehicle vehicle, List<Intersection> route, boolean countAsNew)
            throws InvalidRouteException {
        Road firstRoad = cityMap.getRoad(route.get(0).getId(), route.get(1).getId());
        if (firstRoad == null) {
            throw new InvalidRouteException("route has no road from "
                    + route.get(0).getId() + " to " + route.get(1).getId());
        }
        vehicle.enterRoad(firstRoad, System.currentTimeMillis());
        firstRoad.addVehicle(vehicle);
        if (countAsNew) {
            statistics.recordSpawn();
            publisher.publishVehicleSpawned(vehicle);
        } else {
            publisher.publishVehicleRestored(vehicle);
        }
        return vehicle;
    }

    private Vehicle createRandomVehicle(List<Intersection> route, ThreadLocalRandom random) {
        double roll = random.nextDouble();
        if (roll < EMERGENCY_VEHICLE_CHANCE) {
            return new EmergencyVehicle(route);
        }
        if (roll < EMERGENCY_VEHICLE_CHANCE + BUS_CHANCE) {
            return new Bus(route);
        }
        if (roll < EMERGENCY_VEHICLE_CHANCE + BUS_CHANCE + MOTORCYCLE_CHANCE) {
            return new Motorcycle(route);
        }
        return new Car(route);
    }

    public void stop() {
        running = false;
    }

    public void setSpawnIntervalMultiplier(double multiplier) {
        spawnIntervalMultiplier = Math.max(0.1, multiplier);
    }
}
