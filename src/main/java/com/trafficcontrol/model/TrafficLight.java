package com.trafficcontrol.model;

import com.trafficcontrol.observer.TrafficEventPublisher;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * one light per intersection, controlling both axes together (only one of
 * north-south / east-west is ever green). runs as its own thread so every
 * intersection's timing is independent of every other intersection's.
 *
 * synchronization: a ReentrantLock + Condition is used instead of plain
 * Thread.sleep() specifically so an emergency vehicle can interrupt a
 * phase early via preempt() - sleep() can't be woken up early, await()
 * can. this is the one piece of the engine where "proper synchronization"
 * means more than just guarding a shared counter: it's genuine
 * producer/consumer-style coordination between the light's own thread and
 * whichever thread calls preempt().
 */
public class TrafficLight implements Runnable {

    private static final LightPhase[] CYCLE = {
            LightPhase.NS_GREEN, LightPhase.NS_YELLOW, LightPhase.ALL_RED,
            LightPhase.EW_GREEN, LightPhase.EW_YELLOW, LightPhase.ALL_RED
    };

    private static final long DEFAULT_GREEN_MILLIS = 4500;
    private static final long YELLOW_MILLIS = 1200;
    private static final long ALL_RED_MILLIS = 500;
    static final long MIN_GREEN_MILLIS = 2500;
    static final long MAX_GREEN_MILLIS = 9000;

    private final String intersectionId;
    private final TrafficEventPublisher publisher;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition phaseCondition = lock.newCondition();

    private int cycleIndex = 0;
    private volatile LightPhase phase = CYCLE[0];
    private volatile long nsGreenMillis = DEFAULT_GREEN_MILLIS;
    private volatile long ewGreenMillis = DEFAULT_GREEN_MILLIS;

    // preemption request state - guarded by `lock`, not volatile, because
    // preempt() needs to both set these AND wake the waiting thread as one
    // atomic step (a lone volatile write could race with the await()).
    private boolean preemptRequested = false;
    private Direction preemptDirection = null;

    private volatile boolean running = true;

    public TrafficLight(String intersectionId, TrafficEventPublisher publisher) {
        this.intersectionId = intersectionId;
        this.publisher = publisher;
    }

    @Override
    public void run() {
        try {
            while (running) {
                waitOutCurrentPhase();
                if (!running) {
                    break;
                }
                advancePhase();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // one bad tick should never silently kill the light thread for the whole intersection.
            publisher.publishMessage("traffic light " + intersectionId + " hit an error and stopped: " + e.getMessage());
        }
    }

    private void waitOutCurrentPhase() throws InterruptedException {
        long durationMillis = durationFor(phase);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMillis);

        lock.lock();
        try {
            while (running && !preemptRequested) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return;
                }
                phaseCondition.awaitNanos(remainingNanos);
            }
        } finally {
            lock.unlock();
        }
    }

    private void advancePhase() {
        lock.lock();
        try {
            if (preemptRequested) {
                jumpTowardPreemptedDirection();
                preemptRequested = false;
                preemptDirection = null;
            } else {
                cycleIndex = (cycleIndex + 1) % CYCLE.length;
                phase = CYCLE[cycleIndex];
            }
        } finally {
            lock.unlock();
        }
        publisher.publishLightPhaseChanged(intersectionId, phase);
    }

    /** called while holding `lock`. moves straight to all-red then the requested axis's green on the next tick. */
    private void jumpTowardPreemptedDirection() {
        boolean wantsNorthSouth = preemptDirection.isNorthSouthAxis();
        boolean alreadyGreenForRequest = phase.isGreenFor(preemptDirection);
        if (alreadyGreenForRequest) {
            return; // already what the emergency vehicle needs, nothing to do
        }
        cycleIndex = wantsNorthSouth
                ? indexOf(LightPhase.NS_GREEN)
                : indexOf(LightPhase.EW_GREEN);
        phase = CYCLE[cycleIndex];
    }

    private static int indexOf(LightPhase target) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == target) {
                return i;
            }
        }
        throw new IllegalStateException("phase " + target + " is not part of the cycle");
    }

    private long durationFor(LightPhase p) {
        return switch (p) {
            case NS_GREEN -> nsGreenMillis;
            case EW_GREEN -> ewGreenMillis;
            case NS_YELLOW, EW_YELLOW -> YELLOW_MILLIS;
            case ALL_RED -> ALL_RED_MILLIS;
        };
    }

    /** an emergency vehicle (or anything else) requests this light favor the given direction as soon as possible. */
    public void preempt(Direction direction) {
        lock.lock();
        try {
            preemptRequested = true;
            preemptDirection = direction;
            phaseCondition.signalAll();
        } finally {
            lock.unlock();
        }
        publisher.publishPreemption(intersectionId, direction);
    }

    public boolean isGreenFor(Direction direction) {
        return phase.isGreenFor(direction);
    }

    public LightPhase getPhase() {
        return phase;
    }

    /** called by AdaptiveSignalController to lengthen/shorten the next green phases based on queue load. */
    public void setNsGreenDurationMillis(long millis) {
        this.nsGreenMillis = clamp(millis);
    }

    public void setEwGreenDurationMillis(long millis) {
        this.ewGreenMillis = clamp(millis);
    }

    private static long clamp(long millis) {
        return Math.max(MIN_GREEN_MILLIS, Math.min(MAX_GREEN_MILLIS, millis));
    }

    public void stop() {
        lock.lock();
        try {
            running = false;
            phaseCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
