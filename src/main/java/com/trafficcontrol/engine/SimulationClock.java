package com.trafficcontrol.engine;

/**
 * controls how fast simulated vehicle motion runs relative to real time.
 *
 * why this exists: a 180m city block driven at a realistic 14 m/s takes
 * ~13 real seconds to cross, so at 1x nothing visibly happens in a demo
 * and no vehicle finishes a multi-hop trip in a reasonable viewing
 * window. scaling vehicle travel up makes the simulation watchable
 * without having to pretend city blocks are 20 metres long.
 *
 * note this deliberately only scales *vehicle motion*, not traffic light
 * phases - lights stay on human-readable real-time cycles (a green that
 * lasted 0.5s would be unreadable on screen, and would starve the
 * mover's tick rate). the tradeoff is that reported wait times are in
 * real seconds while distances are covered in scaled time; that's a
 * conscious simulation-design choice, documented here so nobody "fixes"
 * it by accident.
 *
 * the factor is volatile because the gui thread writes it (Nameer's
 * speed slider - see docs/TODO_Nameer.md) while the mover thread reads
 * it every tick.
 */
public class SimulationClock {

    public static final double MIN_SPEED_FACTOR = 1.0;
    public static final double MAX_SPEED_FACTOR = 20.0;
    private static final double DEFAULT_SPEED_FACTOR = 4.0;

    private volatile double speedFactor = DEFAULT_SPEED_FACTOR;

    public double getSpeedFactor() {
        return speedFactor;
    }

    public void setSpeedFactor(double speedFactor) {
        this.speedFactor = Math.max(MIN_SPEED_FACTOR, Math.min(MAX_SPEED_FACTOR, speedFactor));
    }
}
