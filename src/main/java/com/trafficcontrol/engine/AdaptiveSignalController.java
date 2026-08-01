package com.trafficcontrol.engine;

import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.model.TrafficLight;
import com.trafficcontrol.observer.TrafficEventPublisher;

/**
 * this is the "smart" in smart-city: instead of every light running a
 * fixed timer, this thread periodically looks at how many vehicles are
 * queued on each axis of each intersection and lengthens the busier
 * axis's next green phase (and shortens the quieter one) - a simple but
 * genuine load-responsive algorithm rather than a hardcoded cycle.
 *
 * TrafficLight itself clamps whatever we ask for to a sane min/max, so
 * this class can't accidentally starve an axis or leave a light green
 * forever.
 */
public class AdaptiveSignalController implements Runnable {

    private static final long EVALUATION_INTERVAL_MILLIS = 4000;
    private static final long BASE_GREEN_MILLIS = 3000;
    private static final long MILLIS_PER_QUEUED_VEHICLE = 700;

    private final CityMap cityMap;
    private final TrafficEventPublisher publisher;
    private volatile boolean running = true;

    public AdaptiveSignalController(CityMap cityMap, TrafficEventPublisher publisher) {
        this.cityMap = cityMap;
        this.publisher = publisher;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                for (Intersection intersection : cityMap.getIntersections()) {
                    retune(intersection);
                }
                Thread.sleep(EVALUATION_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                publisher.publishMessage("adaptive signal controller hit an error: " + e.getMessage());
            }
        }
    }

    private void retune(Intersection intersection) {
        int northSouthQueue = intersection.getQueueLength(Direction.NORTH) + intersection.getQueueLength(Direction.SOUTH);
        int eastWestQueue = intersection.getQueueLength(Direction.EAST) + intersection.getQueueLength(Direction.WEST);

        TrafficLight light = intersection.getTrafficLight();
        light.setNsGreenDurationMillis(BASE_GREEN_MILLIS + northSouthQueue * MILLIS_PER_QUEUED_VEHICLE);
        light.setEwGreenDurationMillis(BASE_GREEN_MILLIS + eastWestQueue * MILLIS_PER_QUEUED_VEHICLE);
    }

    public void stop() {
        running = false;
    }
}
