package com.trafficcontrol.model;

import java.awt.Color;
import java.util.List;

/**
 * ambulances/fire trucks. these get top priority in intersection queues
 * (see Intersection#enqueue) and can force a traffic light to switch in
 * their favor early (see TrafficLight#preempt) instead of waiting out a
 * normal cycle - modelling real emergency right-of-way behavior.
 */
public class EmergencyVehicle extends Vehicle {

    /** anything queued with this priority or higher jumps to the front of the line. */
    public static final int PRIORITY = 10;

    public EmergencyVehicle(List<Intersection> route) {
        super(route);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public double getSpeedMetersPerSecond() {
        return 22.0; // roughly 80 km/h - sirens on
    }

    @Override
    public Color getColor() {
        return new Color(0xEF4444); // red
    }

    @Override
    public String getTypeName() {
        return "Emergency";
    }
}
