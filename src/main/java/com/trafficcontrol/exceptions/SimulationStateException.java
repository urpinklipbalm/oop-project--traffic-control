package com.trafficcontrol.exceptions;

/**
 * thrown when the simulation is asked to do something that doesn't make
 * sense given its current state - e.g. start() while already running,
 * or stop() while never started. unchecked because these are programming
 * errors (bad call sequencing), not conditions callers are expected to
 * routinely recover from.
 */
public class SimulationStateException extends RuntimeException {

    public SimulationStateException(String message) {
        super(message);
    }
}
