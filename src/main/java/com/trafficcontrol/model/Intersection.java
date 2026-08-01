package com.trafficcontrol.model;

import com.trafficcontrol.observer.TrafficEventPublisher;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
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

    private final String id;
    private final Position position;
    private final TrafficLight trafficLight;
    private final Map<Direction, Deque<Vehicle>> queues = new EnumMap<>(Direction.class);
    private final ReentrantLock queueLock = new ReentrantLock();

    public Intersection(String id, Position position, TrafficEventPublisher publisher) {
        this.id = id;
        this.position = position;
        this.trafficLight = new TrafficLight(id, publisher);
        for (Direction direction : Direction.values()) {
            queues.put(direction, new ArrayDeque<>());
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

    /** returns the next vehicle allowed to cross from this direction, or null if the light is red or the queue is empty. */
    public Vehicle tryDequeue(Direction direction) {
        queueLock.lock();
        try {
            if (!trafficLight.isGreenFor(direction)) {
                return null;
            }
            return queues.get(direction).pollFirst();
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
