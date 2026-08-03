package com.trafficcontrol.model;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * base type for everything that can drive through the city. each concrete
 * subclass (Car, Bus, Motorcycle, EmergencyVehicle) only needs to say how
 * fast it is, how urgently it should be let through, and how it should be
 * drawn - all the route-following/queueing logic lives here so it's not
 * duplicated per vehicle type.
 *
 * a vehicle is only ever moved by one thread (VehicleMover; the
 * Intersection lock guards the moment it's handed between queues), so
 * most of these fields need no synchronization of their own - see
 * docs/ARCHITECTURE.md for the ownership handoff.
 *
 * the exception is where it currently is on the map, which the gui reads
 * from the swing event dispatch thread on every repaint while the mover
 * is writing it. see the RoadPlacement note below.
 */
public abstract class Vehicle {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    public enum TravelState {
        ON_ROAD,
        WAITING_AT_INTERSECTION,
        ARRIVED
    }

    /**
     * which road the vehicle is on and when it joined, as one immutable
     * value. these two always have to agree: pairing a new road with the
     * previous road's entry time would place the vehicle somewhere it has
     * never been.
     *
     * keeping them in a single volatile reference means a reader either
     * sees the whole old placement or the whole new one, never a mix -
     * which two separate fields could not guarantee, since a plain long
     * read isn't even atomic (JLS 17.7) let alone ordered against the
     * reference write next to it.
     */
    private record RoadPlacement(Road road, long entryTimeMillis) {
    }

    private final String id;
    private final List<Intersection> route; // full path from origin to destination, inclusive
    private final long spawnTimeMillis;

    private int routeIndex; // index into route of the intersection we're currently at
    private volatile TravelState travelState; // read by the gui, written by the mover
    private volatile RoadPlacement placement;
    private long waitStartTimeMillis;
    private long cumulativeWaitMillis; // summed across every intersection this trip, for stats

    protected Vehicle(List<Intersection> route) {
        if (route == null || route.size() < 2) {
            throw new IllegalArgumentException("a vehicle needs a route with at least an origin and a destination");
        }
        this.id = getTypeName() + "-" + NEXT_ID.getAndIncrement();
        this.route = route;
        this.spawnTimeMillis = System.currentTimeMillis();
        this.routeIndex = 0;
        this.travelState = TravelState.ON_ROAD;
    }

    // --- behavior every vehicle type must define ---

    /** higher wins when an intersection queue picks who gets to move next. */
    public abstract int getPriority();

    public abstract double getSpeedMetersPerSecond();

    /** how the gui should paint this vehicle. */
    public abstract Color getColor();

    public abstract String getTypeName();

    // --- route bookkeeping (used by VehicleMover / VehicleSpawner) ---

    public String getId() {
        return id;
    }

    public Intersection getOriginIntersection() {
        return route.get(0);
    }

    public Intersection getDestinationIntersection() {
        return route.get(route.size() - 1);
    }

    /** true once the vehicle has reached the last intersection in its route. */
    public boolean isAtDestination() {
        return routeIndex == route.size() - 1;
    }

    /** the next intersection to head toward. only valid when !isAtDestination(). */
    public Intersection getNextIntersection() {
        return route.get(routeIndex + 1);
    }

    /** advances the route pointer once the vehicle reaches the intersection it was heading to. */
    public void advanceToNextIntersection() {
        routeIndex++;
    }

    public TravelState getTravelState() {
        return travelState;
    }

    public void setTravelState(TravelState travelState) {
        this.travelState = travelState;
    }

    public Road getCurrentRoad() {
        RoadPlacement current = placement;
        return current == null ? null : current.road();
    }

    public void enterRoad(Road road, long nowMillis) {
        this.placement = new RoadPlacement(road, nowMillis);
        this.travelState = TravelState.ON_ROAD;
    }

    /**
     * whether enough simulated time has passed to have crossed the current road.
     * speedFactor comes from the engine's SimulationClock - taken as a plain
     * double rather than the clock itself so the model package stays free of
     * any dependency on the engine package.
     */
    public boolean hasFinishedCurrentRoad(long nowMillis, double speedFactor) {
        return getProgressAlongRoad(nowMillis, speedFactor) >= 1.0;
    }

    /**
     * how far along the current road this vehicle is, from 0.0 at the start to
     * 1.0 at the far end. the gui uses this to interpolate a vehicle's position
     * between the two intersections when drawing it.
     */
    public double getProgressAlongRoad(long nowMillis, double speedFactor) {
        // read the placement once - re-reading the field could pick up a different
        // road partway through the calculation and mix the two up.
        RoadPlacement current = placement;
        if (current == null) {
            return 0.0;
        }
        double travelTimeMillis = current.road().getTravelTimeMillis(getSpeedMetersPerSecond()) / speedFactor;
        if (travelTimeMillis <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (nowMillis - current.entryTimeMillis()) / travelTimeMillis);
    }

    public void startWaiting(long nowMillis) {
        this.travelState = TravelState.WAITING_AT_INTERSECTION;
        this.waitStartTimeMillis = nowMillis;
    }

    /** how long the current wait (since the last startWaiting call) has lasted so far. */
    public long getWaitTimeMillis(long nowMillis) {
        return nowMillis - waitStartTimeMillis;
    }

    /** folds a completed wait into the trip's running total - call once per intersection, right before moving on. */
    public void addWaitTime(long millis) {
        cumulativeWaitMillis += millis;
    }

    public long getCumulativeWaitTimeMillis() {
        return cumulativeWaitMillis;
    }

    public long getTotalTravelTimeMillis(long nowMillis) {
        return nowMillis - spawnTimeMillis;
    }

    @Override
    public String toString() {
        return id;
    }
}
