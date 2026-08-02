package com.trafficcontrol;

import com.trafficcontrol.engine.SimulationEngine;
import com.trafficcontrol.model.CityMap;
import com.trafficcontrol.model.Direction;
import com.trafficcontrol.model.Intersection;
import com.trafficcontrol.observer.TrafficObserver;
import com.trafficcontrol.persistence.DefaultCityMapFactory;
import com.trafficcontrol.persistence.EventLogger;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * headless verification of the simulation engine - runs it without the
 * gui and prints what actually happened, so the concurrency can be
 * checked independently of any ui bugs.
 *
 * run it with: mvn exec:java -Dexec.mainClass=com.trafficcontrol.SmokeTest
 * (optionally pass a run duration in seconds, default 20).
 *
 * what a healthy run looks like:
 * - arrived count climbing steadily, not stuck at 0
 * - avg-wait and max-wait non-zero (vehicles really are queueing at reds)
 * - queued > 0 at least some of the time (congestion is forming, which is
 *   what AdaptiveSignalController exists to respond to)
 * - preemptions > 0 over a long enough run (emergency vehicles are
 *   interrupting light cycles)
 * - "threads after stop" is 1 - every worker thread shut down cleanly
 *   and nothing was left running
 */
public final class SmokeTest {

    public static void main(String[] args) throws Exception {
        int runSeconds = args.length > 0 ? Integer.parseInt(args[0]) : 20;

        CityMap cityMap = DefaultCityMapFactory.createDefaultGrid();
        SimulationEngine engine = new SimulationEngine(cityMap);
        EventLogger logger = new EventLogger(Path.of("logs"));
        engine.addObserver(logger);

        AtomicInteger preemptions = new AtomicInteger();
        AtomicInteger arrivals = new AtomicInteger();
        AtomicInteger maxWaitSeen = new AtomicInteger();

        engine.addObserver(new TrafficObserver() {
            @Override
            public void onSimulationMessage(String message) {
                System.out.println("[msg] " + message);
            }

            @Override
            public void onVehicleArrived(com.trafficcontrol.model.Vehicle v, long wait, long travel) {
                arrivals.incrementAndGet();
                maxWaitSeen.accumulateAndGet((int) wait, Math::max);
            }

            @Override
            public void onPreemption(String intersectionId, Direction direction) {
                preemptions.incrementAndGet();
            }
        });

        System.out.println("running for " + runSeconds + "s...");
        engine.start();

        for (int elapsed = 5; elapsed <= runSeconds; elapsed += 5) {
            Thread.sleep(5000);
            System.out.println("  t=" + elapsed + "s  " + engine.getStatistics()
                    + "  | queued=" + totalQueued(cityMap)
                    + ", busiest=" + busiestIntersection(cityMap));
        }

        engine.stop();
        logger.close();

        System.out.println();
        System.out.println("final stats:      " + engine.getStatistics());
        System.out.println("arrivals:         " + arrivals.get());
        System.out.println("max wait seen:    " + maxWaitSeen.get() + "ms");
        System.out.println("preemptions:      " + preemptions.get());
        System.out.println("threads after stop: " + Thread.activeCount() + " (expect 1 - just main)");
    }

    private static int totalQueued(CityMap cityMap) {
        int total = 0;
        for (Intersection intersection : cityMap.getIntersections()) {
            total += intersection.getTotalQueueLength();
        }
        return total;
    }

    private static String busiestIntersection(CityMap cityMap) {
        Intersection busiest = null;
        int max = -1;
        for (Intersection intersection : cityMap.getIntersections()) {
            int queued = intersection.getTotalQueueLength();
            if (queued > max) {
                max = queued;
                busiest = intersection;
            }
        }
        return busiest + "(" + max + ")";
    }
}
