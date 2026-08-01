package com.trafficcontrol.model;

/**
 * a simple immutable (x, y) grid coordinate. used to place intersections
 * on the map and, later, for the gui to know where to paint things.
 * kept deliberately dumb - no behavior beyond holding two numbers.
 */
public final class Position {

    private final double x;
    private final double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
