package com.trafficcontrol.model;

import java.awt.Color;
import java.util.List;

/** small and quick - fastest normal vehicle on the road, same priority as a car at intersections. */
public class Motorcycle extends Vehicle {

    public Motorcycle(List<Intersection> route) {
        super(route);
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public double getSpeedMetersPerSecond() {
        return 18.0; // roughly 65 km/h
    }

    @Override
    public Color getColor() {
        return new Color(0x10B981); // green
    }

    @Override
    public String getTypeName() {
        return "Motorcycle";
    }
}
