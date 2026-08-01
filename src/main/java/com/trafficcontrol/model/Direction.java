package com.trafficcontrol.model;

/**
 * the compass side a road approaches an intersection from. queues at an
 * intersection are keyed by this, e.g. Direction.SOUTH means "vehicles
 * arriving from the south, wanting to cross northbound".
 */
public enum Direction {
    NORTH, SOUTH, EAST, WEST;

    /** true for the north/south pair, false for east/west - used to decide which light axis controls this direction. */
    public boolean isNorthSouthAxis() {
        return this == NORTH || this == SOUTH;
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
