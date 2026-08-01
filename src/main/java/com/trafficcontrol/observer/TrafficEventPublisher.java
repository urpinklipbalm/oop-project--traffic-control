package com.trafficcontrol.observer;

import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.LightPhase;
import com.trafficcontrol.model.Vehicle;

/**
 * the narrow interface engine-side classes (TrafficLight, VehicleMover,
 * VehicleSpawner...) publish events through. SimulationEngine is the only
 * implementer - it fans each call out to every registered TrafficObserver.
 *
 * kept separate from TrafficObserver on purpose: this is "how components
 * report what happened" (one-to-one, narrow), that is "how outside code
 * reacts to what happened" (one-to-many, broad default methods). collapsing
 * them into one interface would force every publisher to also carry
 * subscription management it has no business owning.
 */
public interface TrafficEventPublisher {

    void publishVehicleSpawned(Vehicle vehicle);

    void publishVehicleArrived(Vehicle vehicle, long waitTimeMillis, long travelTimeMillis);

    void publishLightPhaseChanged(String intersectionId, LightPhase phase);

    void publishPreemption(String intersectionId, Direction direction);

    void publishMessage(String message);
}
