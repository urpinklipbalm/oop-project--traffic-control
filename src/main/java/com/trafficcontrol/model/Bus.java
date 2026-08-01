package com.trafficcontrol.model;

import java.awt.Color;
import java.util.List;

/** bigger and slower than a car, and gets a slight priority bump since it carries more passengers. */
public class Bus extends Vehicle {

    public Bus(List<Intersection> route) {
        super(route);
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public double getSpeedMetersPerSecond() {
        return 10.0; // roughly 36 km/h - slower to accelerate/brake
    }

    @Override
    public Color getColor() {
        return new Color(0xF59E0B); // amber
    }

    @Override
    public String getTypeName() {
        return "Bus";
    }
}
