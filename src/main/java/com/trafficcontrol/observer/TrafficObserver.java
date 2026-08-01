package com.trafficcontrol.observer;

import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;

/**
 * anything that wants to react to the simulation implements this - the
 * file-based EventLogger, the gui's MainFrame/StatisticsPanel, whatever
 * else gets added later. default (no-op) methods mean an implementer only
 * overrides the events it actually cares about instead of stubbing all of
 * them out.
 *
 * important for implementers: these callbacks fire from background
 * simulation threads (traffic light / mover / spawner), never the swing
 * event dispatch thread. any implementation that touches a Swing
 * component MUST hop over with SwingUtilities.invokeLater - see
 * gui/MainFrame.java for the pattern.
 */
public interface TrafficObserver {

    default void onVehicleSpawned(Vehicle vehicle) {
    }

    default void onVehicleArrived(Vehicle vehicle, long waitTimeMillis, long travelTimeMillis) {
    }

    default void onLightPhaseChanged(String intersectionId, LightPhase phase) {
    }

    default void onPreemption(String intersectionId, Direction direction) {
    }

    default void onSimulationMessage(String message) {
    }
}
