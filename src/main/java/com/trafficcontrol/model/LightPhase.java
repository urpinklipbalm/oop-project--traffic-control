package com.trafficcontrol.model;

/**
 * the state of a single intersection's traffic light. one light controls
 * both axes (north-south and east-west) together, the way a real
 * intersection does - only one axis is ever green at a time.
 *
 * the full cycle (owned by TrafficLight, not this enum, since ALL_RED
 * appears twice with different "what's next" targets):
 * NS_GREEN -> NS_YELLOW -> ALL_RED -> EW_GREEN -> EW_YELLOW -> ALL_RED -> (repeat)
 */
public enum LightPhase {
    NS_GREEN,
    NS_YELLOW,
    EW_GREEN,
    EW_YELLOW,
    ALL_RED;

    /** whether a vehicle arriving from the given direction is allowed to cross right now. */
    public boolean isGreenFor(Direction direction) {
        return direction.isNorthSouthAxis() ? this == NS_GREEN : this == EW_GREEN;
    }
}
