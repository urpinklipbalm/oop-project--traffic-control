package com.trafficcontrol.model;

import java.awt.Color;
import java.util.List;

/** the everyday vehicle - average speed, no special treatment at intersections. */
public class Car extends Vehicle {

    public Car(List<Intersection> route) {
        super(route);
    }

    public Car(List<Intersection> route, String customLabel) {
        super(route, customLabel);
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public double getSpeedMetersPerSecond() {
        return 14.0; // roughly 50 km/h
    }

    @Override
    public Color getColor() {
        return new Color(0x3B82F6); // blue
    }

    @Override
    public String getTypeName() {
        return "Car";
    }
}
