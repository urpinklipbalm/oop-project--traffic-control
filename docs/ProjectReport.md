# Project Report - Smart City Traffic Control

CS-212 Object Oriented Programming, Summer 2026. Domain: Traffic Control
in a Smart City.

*This is a living document - update it as you build your part, not all
at once at the end. Convert to `ProjectReport.pdf` for the final
submission zip (see "Converting to PDF" at the bottom).*

## 1. Introduction

A multithreaded simulation of a small smart-city road grid: vehicles
spawn, route across a 3×3 intersection grid via shortest-path BFS, and
move through it obeying traffic lights that run independently per
intersection and adapt their timing to real-time congestion. Emergency
vehicles get right-of-way, both in intersection queues and by preempting
a light's normal cycle. The goal was a project where the concurrency
isn't decorative - the threads genuinely need to coordinate over shared
state (light phases, intersection queues) for the simulation to behave
correctly, which is what the traffic-control domain naturally gives you.

## 2. System Design

See [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) for the full package
breakdown and design rationale, and
[`docs/uml/class-diagram.md`](uml/class-diagram.md) for the class
diagram. Summary of the major design decisions:

- **`Vehicle` is abstract**, with `Car`/`Bus`/`Motorcycle`/`EmergencyVehicle`
  only overriding speed, priority, color, and type name - all
  route-following logic lives once in the base class.
- **Two narrow interfaces instead of one broad one** for events:
  `TrafficEventPublisher` (how engine components report what happened)
  and `TrafficObserver` (how outside code reacts). `SimulationEngine` is
  the only class that implements the former and fans events out to every
  registered instance of the latter - this is what let the file logger
  and the gui get built independently against the same engine, by two
  different people, without either one touching engine internals.
- **A `PersistenceService` interface exists before a "real"
  implementation does** - `DefaultCityMapFactory` is an intentionally
  temporary hardcoded stand-in, so the whole app was runnable from very
  early on rather than only once every piece was finished.

### How the UML evolved during coding

*[Fill this in as the project progresses - what changed between the
first draft of the class diagram and what's actually in the code, and
why. Some likely candidates worth recording if they happen: whether
`TrafficLight` stayed one-per-intersection vs. one-per-direction, how
`PersistenceService`'s method signatures changed once Ayesha actually
implemented against them, any classes that got merged/split once the gui
was built.]*

## 3. OOP Principles

- **Inheritance & polymorphism:** `Vehicle` → `Car`/`Bus`/`Motorcycle`/
  `EmergencyVehicle`. `VehicleMover` and `Intersection` never branch on
  vehicle type - `intersection.enqueue()` decides queue position purely
  from `vehicle.getPriority()`, and `VehicleMover` only checks
  `instanceof EmergencyVehicle` once, specifically to trigger the
  emergency-only preemption behavior (a deliberate, narrow use of type
  checking rather than scattering it through generic queue logic).
- **Encapsulation:** every piece of shared mutable state (an
  `Intersection`'s queues, a `TrafficLight`'s phase, `SimulationStatistics`'s
  counters) is private with synchronized/atomic-guarded access only -
  nothing is reached into directly from outside its owning class.
- **Interfaces over concrete coupling:** `TrafficObserver`,
  `TrafficEventPublisher`, `PersistenceService` - see System Design
  above.
- **Custom exceptions** carry meaning `Exception`/`RuntimeException`
  alone wouldn't: `InvalidRouteException` (checked - no path between two
  intersections), `CityMapLoadException` (checked - bad persistence
  file), `SimulationStateException` (unchecked - engine API misuse like
  double-`start()`).

## 4. Concurrency & Synchronization

*(Owner: repo owner - see [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) for
the full reasoning; this section is the report-facing summary.)*

Every intersection's `TrafficLight` runs as its own thread, using a
`ReentrantLock` + `Condition` rather than `Thread.sleep()` specifically
so an `EmergencyVehicle` can interrupt a phase early
(`TrafficLight.preempt()`) instead of waiting out a full cycle - this is
genuine producer/consumer-style coordination, not just parallel
independent timers. Each `Intersection`'s vehicle queues are guarded by
their own lock (not one lock for the whole city), so contention at one
junction never blocks another. Statistics use `AtomicLong`/`AtomicBoolean`
where no multi-field invariant needs protecting. `SimulationEngine.start()`/
`stop()` use `AtomicBoolean.compareAndSet` to make start/stop transitions
race-safe, and `stop()` does a graceful `shutdown()` + bounded
`awaitTermination()` so no worker thread is ever abandoned.

Adding the animated map introduced a tenth thread reading this state -
Swing's event dispatch thread, repainting ~30 times a second. It is
strictly read-only, and every value it touches was made safe to read
concurrently: copy-on-write vehicle lists, a volatile light phase, queue
lengths taken under the intersection lock, and a vehicle's position
published as a single immutable snapshot.

That last one was a genuine race the map exposed. The current road and
the time the vehicle joined it were two separate plain fields, so a
reader could pick up the *new* road paired with the *old* entry time and
draw a vehicle somewhere it had never been - and a non-volatile `long`
read is not even guaranteed atomic (JLS 17.7). Pairing them into one
immutable `RoadPlacement` behind a single `volatile` reference means a
reader always sees a consistent pair.

**Challenges faced:** *[fill in anything that was genuinely tricky - e.g.
getting the preemption wake-up right without missing a signal, or tuning
tick intervals so the simulation is visibly active without spawning
vehicles faster than they can be processed. The race above is worth
writing up properly - it is a good example of a bug that only appeared
once a second component started reading shared state.]*

## 5. File Handling

*(Owner: Ayesha Kamran - see [`docs/TODO_Ayesha.md`](TODO_Ayesha.md).)*

*[Fill in: what `CsvCityMapLoader` does, the file format you settled on
and why, how you handle a missing/corrupted file, what the snapshot
save/resume format is (and why you picked it over alternatives), what
the statistics export looks like. Screenshots of a sample file are
welcome here.]*

## 6. User Interface

*(Owner: Nameer Ahmed - see [`docs/TODO_Nameer.md`](TODO_Nameer.md).)*

*[Fill in: layout decisions, how `CityPanel` renders/animates vehicles,
styling choices on top of FlatLaf, anything tricky about painting on a
timer vs. reacting to observer callbacks. Screenshots of the final
dashboard belong here.]*

## 7. Challenges Faced (project-wide)

*[Fill in as they come up - e.g. environment setup differences across
3 machines, a design decision that had to be revisited once a teammate
started building against an interface, anything about getting the three
parts integrated.]*

## 8. Task Division

See [`docs/TASK_DIVISION.md`](TASK_DIVISION.md) for the full breakdown
and reasoning. Summary: the repo owner built the domain model,
concurrency engine, and a minimal working placeholder GUI/file-logger so
the whole app was runnable early; Ayesha built the real file persistence
layer on top of the `PersistenceService` interface; Nameer built the
real dashboard on top of the `TrafficObserver` interface.

## 9. Testing / Verification

*[Fill in: how each part was verified - the repo owner's engine was
smoke-tested headlessly (start the simulation, run it for N seconds,
confirm vehicles are spawned/arrive/stats update, confirm clean thread
shutdown) before the gui was wired in. Ayesha and Nameer should record
how they verified their parts - e.g. loading a deliberately malformed
CSV and confirming a clean `CityMapLoadException` rather than a crash;
resizing the gui window and confirming nothing clips.]*

## 10. Conclusion

*[Fill in once the project is complete - what you'd do differently, what
you're proud of.]*

---

## Converting to PDF

This report is kept as Markdown during development (easy to diff/review
in git). For the final submission zip, which needs `ProjectReport.pdf`:

- **Easiest:** open this file in VS Code with the "Markdown PDF"
  extension installed, then "Markdown PDF: Export (pdf)" from the
  command palette.
- **Alternative:** open the rendered GitHub preview of this file in a
  browser and use "Print -> Save as PDF".
- **If you have a LaTeX toolchain installed:** `pandoc ProjectReport.md
  -o ProjectReport.pdf --pdf-engine=xelatex` (or `pdflatex`).
