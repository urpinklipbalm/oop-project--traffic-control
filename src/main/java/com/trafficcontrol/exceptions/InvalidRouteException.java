package com.trafficcontrol.exceptions;

/**
 * thrown when the city map has no path between two intersections.
 * this is a checked exception on purpose: callers (route planning during
 * vehicle spawn) must decide what to do when a vehicle can't reach its
 * destination, rather than letting it fail silently.
 */
public class InvalidRouteException extends Exception {

    public InvalidRouteException(String message) {
        super(message);
    }
}
