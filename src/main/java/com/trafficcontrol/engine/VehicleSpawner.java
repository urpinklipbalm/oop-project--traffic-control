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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * periodically creates a new vehicle with a random origin/destination and
 * drops it onto the first road of its route. runs on its own thread so
 * spawning never blocks (or is blocked by) vehicles already moving.
 */
public class VehicleSpawner implements Runnable {

    private static final long MIN_SPAWN_INTERVAL_MILLIS = 1200;
    private static final long MAX_SPAWN_INTERVAL_MILLIS = 2800;
    private static final double EMERGENCY_VEHICLE_CHANCE = 0.06;
    private static final double BUS_CHANCE = 0.18;
    private static final double MOTORCYCLE_CHANCE = 0.20;

    private final CityMap cityMap;
    private final TrafficEventPublisher publisher;
    private final SimulationStatistics statistics;
    private volatile boolean running = true;

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
                Thread.sleep(ThreadLocalRandom.current().nextLong(MIN_SPAWN_INTERVAL_MILLIS, MAX_SPAWN_INTERVAL_MILLIS));
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
        String originId = ids.get(random.nextInt(ids.size()));
        String destinationId;
        do {
            destinationId = ids.get(random.nextInt(ids.size()));
        } while (destinationId.equals(originId));

        List<Intersection> route = cityMap.getRoute(originId, destinationId);
        Road firstRoad = cityMap.getRoad(route.get(0).getId(), route.get(1).getId());
        if (firstRoad == null) {
            return; // shouldn't happen given getRoute succeeded, but stay defensive about it
        }

        Vehicle vehicle = createRandomVehicle(route, random);
        vehicle.enterRoad(firstRoad, System.currentTimeMillis());
        firstRoad.addVehicle(vehicle);

        statistics.recordSpawn();
        publisher.publishVehicleSpawned(vehicle);
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
}
