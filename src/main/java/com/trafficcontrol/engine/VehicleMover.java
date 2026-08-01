package com.trafficcontrol.engine;

import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.EmergencyVehicle;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.Road;
import com.trafficcontrol.model.Vehicle;
import com.trafficcontrol.observer.TrafficEventPublisher;

/**
 * the heartbeat of the simulation: on every tick it (1) checks every road
 * for vehicles that have finished crossing it and moves them into the
 * next intersection's queue, then (2) checks every intersection for
 * queued vehicles the light has just turned green for and sends them
 * onto their next road (or marks them arrived).
 *
 * roads and intersections are touched by exactly this one thread for
 * writes (Intersection's own lock still guards against the spawner/gui
 * reading concurrently), so there's no risk of two movers racing each
 * other - by design we only ever run one VehicleMover.
 */
public class VehicleMover implements Runnable {

    private static final long TICK_MILLIS = 150;

    private final CityMap cityMap;
    private final TrafficEventPublisher publisher;
    private final SimulationStatistics statistics;
    private volatile boolean running = true;

    public VehicleMover(CityMap cityMap, TrafficEventPublisher publisher, SimulationStatistics statistics) {
        this.cityMap = cityMap;
        this.publisher = publisher;
        this.statistics = statistics;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                long now = System.currentTimeMillis();
                advanceRoads(now);
                releaseQueues(now);
                Thread.sleep(TICK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                publisher.publishMessage("vehicle mover hit an error: " + e.getMessage());
            }
        }
    }

    private void advanceRoads(long now) {
        for (Road road : cityMap.getRoads()) {
            // vehiclesOnRoad is a CopyOnWriteArrayList, so it's safe to iterate here
            // while removeVehicle() mutates it below (and while the gui reads it for rendering).
            for (Vehicle vehicle : road.getVehiclesOnRoad()) {
                if (vehicle.hasFinishedCurrentRoad(now)) {
                    arriveAtIntersection(road, vehicle, now);
                }
            }
        }
    }

    private void arriveAtIntersection(Road road, Vehicle vehicle, long now) {
        road.removeVehicle(vehicle);
        vehicle.advanceToNextIntersection();
        Intersection intersection = road.getTo();

        if (vehicle.isAtDestination()) {
            completeTrip(vehicle, now);
            return;
        }

        Direction arrivalDirection = road.getApproachDirection();
        vehicle.startWaiting(now);
        intersection.enqueue(arrivalDirection, vehicle);

        if (vehicle instanceof EmergencyVehicle) {
            intersection.getTrafficLight().preempt(arrivalDirection);
        }
    }

    private void releaseQueues(long now) {
        for (Intersection intersection : cityMap.getIntersections()) {
            for (Direction direction : Direction.values()) {
                Vehicle vehicle = intersection.tryDequeue(direction);
                if (vehicle == null) {
                    continue;
                }
                vehicle.addWaitTime(vehicle.getWaitTimeMillis(now));
                sendToNextRoad(intersection, vehicle, now);
            }
        }
    }

    private void sendToNextRoad(Intersection intersection, Vehicle vehicle, long now) {
        Intersection nextHop = vehicle.getNextIntersection();
        Road nextRoad = cityMap.getRoad(intersection.getId(), nextHop.getId());
        if (nextRoad == null) {
            // route said this edge exists but the map disagrees - drop the vehicle rather than get it stuck forever
            publisher.publishMessage("no road from " + intersection.getId() + " to " + nextHop.getId() + " - dropping " + vehicle.getId());
            return;
        }
        vehicle.enterRoad(nextRoad, now);
        nextRoad.addVehicle(vehicle);
    }

    private void completeTrip(Vehicle vehicle, long now) {
        vehicle.setTravelState(Vehicle.TravelState.ARRIVED);
        long totalWait = vehicle.getCumulativeWaitTimeMillis();
        long totalTravel = vehicle.getTotalTravelTimeMillis(now);
        statistics.recordArrival(totalWait, totalTravel);
        publisher.publishVehicleArrived(vehicle, totalWait, totalTravel);
    }

    public void stop() {
        running = false;
    }
}
