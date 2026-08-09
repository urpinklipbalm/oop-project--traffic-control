package com.trafficcontrol.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * a single junction: one TrafficLight plus one waiting queue per approach
 * direction. this is the most contended piece of shared state in the
 * whole simulation - VehicleMover threads enqueue/dequeue from many
 * intersections concurrently - so every access to the queues goes through
 * `queueLock`. the lock is per-intersection (not one global lock for the
 * whole city), so traffic at one junction never blocks traffic at another.
 */
public class Intersection {

    /**
     * minimum gap between two vehicles crossing from the same direction.
     * without this the mover would release one vehicle per direction on
     * every 150ms tick, which is both unrealistic (real junctions clear
     * roughly one car every couple of seconds) and hides congestion
     * entirely - queues would never build up for the adaptive controller
     * to respond to.
     */
    private static final long RELEASE_HEADWAY_MILLIS = 300;

    private final String id;
    private final Position position;
    private final TrafficLight trafficLight;
    private final Map<Direction, Deque<Vehicle>> queues = new EnumMap<>(Direction.class);
    private final Map<Direction, Long> lastReleaseTime = new EnumMap<>(Direction.class);
    private final ReentrantLock queueLock = new ReentrantLock();

    public Intersection(String id, Position position) {
        this.id = id;
        this.position = position;
        this.trafficLight = new TrafficLight(id);
        for (Direction direction : Direction.values()) {
            queues.put(direction, new ArrayDeque<>());
            lastReleaseTime.put(direction, 0L);
        }
    }

    public String getId() {
        return id;
    }

    public Position getPosition() {
        return position;
    }

    public TrafficLight getTrafficLight() {
        return trafficLight;
    }

    /**
     * adds a vehicle to the queue for the direction it arrived from.
     * higher-priority vehicles (emergency vehicles) jump to the front of
     * the line instead of waiting their turn - modelled with getPriority()
     * so this method never needs to know about specific vehicle subtypes.
     */
    public void enqueue(Direction direction, Vehicle vehicle) {
        queueLock.lock();
        try {
            Deque<Vehicle> queue = queues.get(direction);
            if (vehicle.getPriority() >= EmergencyVehicle.PRIORITY) {
                queue.addFirst(vehicle);
            } else {
                queue.addLast(vehicle);
            }
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * returns the next vehicle allowed to cross from this direction, or null if
     * the light is red, the queue is empty, or the previous vehicle from this
     * direction left too recently (see RELEASE_HEADWAY_MILLIS).
     */
    public Vehicle tryDequeue(Direction direction, long nowMillis) {
        queueLock.lock();
        try {
            if (!trafficLight.isGreenFor(direction)) {
                return null;
            }
            if (nowMillis - lastReleaseTime.get(direction) < RELEASE_HEADWAY_MILLIS) {
                return null;
            }
            Vehicle vehicle = queues.get(direction).pollFirst();
            if (vehicle != null) {
                lastReleaseTime.put(direction, nowMillis);
            }
            return vehicle;
        } finally {
            queueLock.unlock();
        }
    }

    public int getQueueLength(Direction direction) {
        queueLock.lock();
        try {
            return queues.get(direction).size();
        } finally {
            queueLock.unlock();
        }
    }

    /** immutable snapshot in real queue order for safe gui rendering. */
    public List<Vehicle> getQueuedVehicles(Direction direction) {
        queueLock.lock();
        try {
            return List.copyOf(queues.get(direction));
        } finally {
            queueLock.unlock();
        }
    }

    public int getTotalQueueLength() {
        queueLock.lock();
        try {
            int total = 0;
            for (Deque<Vehicle> queue : queues.values()) {
                total += queue.size();
            }
            return total;
        } finally {
            queueLock.unlock();
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
