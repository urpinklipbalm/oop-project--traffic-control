# Architecture

A 3×3 grid city simulation: vehicles spawn at random intersections,
route to a random destination, and move through the grid obeying
per-intersection traffic lights. See `docs/uml/class-diagram.md` for the
full class diagram; this doc explains *why* it's shaped the way it is
and where each teammate's work plugs in.

## Package layout

```
com.trafficcontrol
├── Main.java              entry point - wires everything together
├── model/                 domain objects: Vehicle hierarchy, Road,
│                          Intersection, TrafficLight, CityMap
├── engine/                the concurrency core: SimulationEngine and
│                          its worker threads (spawner, mover, adaptive
│                          signal controller), SimulationStatistics
├── observer/              TrafficObserver / TrafficEventPublisher -
│                          the seam everything else plugs into
├── persistence/           file handling: EventLogger (done),
│                          PersistenceService + DefaultCityMapFactory
│                          (Ayesha's real implementation goes here)
├── gui/                   MainFrame (window/controls) and CityPanel
│                          (the animated map); ControlPanel and
│                          StatisticsPanel are Nameer's
└── exceptions/            InvalidRouteException, CityMapLoadException,
                           SimulationStateException
```

## The two integration seams

The whole point of splitting this project three ways without everyone
stepping on each other's files is these two interfaces:

**`TrafficObserver`** (in `observer/`) - anything that wants to react to
the simulation implements this. `EventLogger` and `MainFrame` both do.
The engine never imports either of them; it only knows about the
interface. This means Nameer's real dashboard classes (`CityPanel`,
`StatisticsPanel`, etc.) can be added and can subscribe to the engine
without a single line of `engine/` or `model/` code changing.

**`PersistenceService`** (in `persistence/`) - the contract for loading
and saving city maps and exporting stats. `DefaultCityMapFactory` is a
throwaway stand-in (hardcoded grid) implementing the same *shape* of
result (a `CityMap`) without implementing the interface itself, so
swapping it for Ayesha's real `CsvCityMapLoader` is a one-line change in
`Main.java`.

## Why the engine is built the way it is

- **One `TrafficLight` thread per intersection.** Each intersection's
  signal timing is fully independent of every other intersection - no
  shared state between them, so no lock needed across intersections.
- **A lock+`Condition` inside `TrafficLight`, not `Thread.sleep()`.**
  Sleep can't be interrupted early. An emergency vehicle needs to be
  able to force a phase change *now*, not wait out the current cycle -
  see `TrafficLight.preempt()`.
- **A `ReentrantLock` per `Intersection`, not one global lock.** The
  vehicle queues are the most contended shared state in the whole
  simulation (`VehicleMover` touches every intersection every tick).
  Locking per-intersection means traffic at one junction never blocks
  traffic at another.
- **Exactly one `VehicleMover` thread.** It's the only thing that writes
  to roads/queues (besides `Intersection`'s own lock-guarded methods),
  so there's no risk of two movers racing each other. If this were ever
  parallelized (e.g. one mover per region of the city), the existing
  per-intersection locks would already make that safe.
- **`AtomicLong`/`AtomicBoolean` where the state is a single independent
  counter or flag** (statistics, `SimulationEngine.running`) - no lock
  needed since there's no multi-field invariant to protect.
- **`CopyOnWriteArrayList`** for the observer list and each road's
  `vehiclesOnRoad` - both are read far more often (every gui repaint,
  every tick) than written (spawn/arrive), so lock-free reads are worth
  the extra cost on the rare write.

## Vehicle lifecycle (how a trip actually happens)

1. `VehicleSpawner` picks a random origin/destination pair, computes a
   route via `CityMap.getRoute()` (BFS), creates a random `Vehicle`
   subtype, and drops it onto the first `Road` of that route.
2. Every tick, `VehicleMover` checks every road for vehicles that have
   "arrived" (enough simulated time has passed for their speed/road
   length) and moves them into the destination intersection's queue for
   the direction they arrived from.
3. Still every tick, `VehicleMover` asks each `Intersection` for any
   vehicle its light has just turned green for. That vehicle either
   completes its trip (if this was its last intersection) or gets sent
   onto the next road in its route.
4. `EmergencyVehicle`s jump straight to the front of whatever queue they
   join, and trigger `TrafficLight.preempt()` for their direction the
   moment they arrive - not something the *intersection* decides, the
   *mover* decides it based on the vehicle's runtime type (a concrete,
   deliberate use of polymorphism rather than an `instanceof` chain
   scattered through `Intersection`).

## The gui as a tenth reader thread

`CityPanel` repaints ~30 times a second on the Swing event dispatch
thread, reading simulation state while nine light threads and the mover
are writing it. It is deliberately read-only - it never calls back into
the engine - so it cannot disturb the simulation whenever a frame lands.
Every value it reads was made safe to read concurrently:

- a road's vehicle list is copy-on-write, so it can be iterated mid-write
- a light's phase is `volatile`
- a queue length is taken under that intersection's own lock
- a vehicle's position is published as a single immutable
  `RoadPlacement` snapshot behind one `volatile` reference

That last one was a real bug found while building the map. Previously
the current road and the time it was joined were two separate plain
fields, so a reader could pick up the new road paired with the old
entry time and draw the vehicle somewhere it had never been - and a
non-volatile `long` read isn't even atomic (JLS 17.7). Pairing them in
one immutable value means a reader sees either the whole old placement
or the whole new one.

## Threading model at a glance

`SimulationEngine.start()` submits: one `TrafficLight` per intersection
(9 in the default grid) + `VehicleSpawner` + `VehicleMover` +
`AdaptiveSignalController`, all onto one `ExecutorService`. `stop()`
signals every one of them to stop via their own `stop()` method, then
does a graceful `shutdown()` + bounded `awaitTermination()`, falling
back to `shutdownNow()` if anything's still stuck after 3 seconds - no
worker thread is ever just abandoned.
