# Initial Project Plan

*This is the plan the repo owner worked from to scaffold the project and
build their part. Kept as-is (not edited after the fact) so the rest of
the team can see the reasoning behind the architecture, not just the
result. Day-to-day status lives in `README.md`'s status table and
`docs/TASK_DIVISION.md`, not here - this file is a record, not a live
tracker.*

## Context

CS-212 (OOP, Summer 2026) final group project, domain: Traffic Control
in a Smart City. Group of 3: repo owner, Ayesha Kamran
(`@Ayesha-Kamran`), Nameer Ahmed (`@nameer451`).

Grading requires: a UML diagram, deep OOP (inheritance/polymorphism/
encapsulation), a GUI (Swing/JavaFX), permanent file storage, real Java
threading with proper synchronization on shared data, a clean trackable
GitHub history from all 3 members, and a project report covering design,
challenges, and task division.

The goal of the initial scaffolding session was to: (1) design the whole
system and its UML so all 3 members work against one coherent
architecture, (2) fully implement the repo owner's own part - the core
domain model + concurrency engine, the most technically demanding slice
- end-to-end and runnable, (3) leave a minimal-but-working GUI/persistence
scaffold so the app is demoable immediately instead of only integrating
at the last minute, and (4) write precise, unambiguous TODO docs for
Ayesha and Nameer so they know exactly what to build, in what files,
against what interfaces.

## Tech stack decision

- **Swing** for the GUI - built into the JDK, no module-path setup
  required across 3 different dev machines.
- **FlatLaf** (`com.formdev:flatlaf`) for a clean modern look with
  near-zero extra code, instead of hand-styling default Swing or taking
  on JavaFX's heavier setup.
- **Maven** for build/dependency management.
- Java 21.

## Domain design

A 3×3 grid city (9 intersections, connecting roads). Vehicles spawn,
navigate via BFS routing over the grid, and pass through intersections
gated by traffic lights. An adaptive controller watches queue lengths
and lengthens/shortens green time - a genuine load-responsive algorithm
rather than a fixed timer, for the Logic & Efficiency rubric row.

## Concurrency design

One `TrafficLight` thread per intersection (cycles an intersection-wide
phase via a lock + `Condition`, so an `EmergencyVehicle` can preempt a
phase early instead of waiting out the cycle). Each `Intersection`
guards its own vehicle queues with its own `ReentrantLock`. One
`SimulationEngine`-owned `ExecutorService` runs all the `TrafficLight`
threads plus one `VehicleSpawner`, one `VehicleMover`, and one
`AdaptiveSignalController`. `start()`/`stop()` use `AtomicBoolean` for
race-safe state transitions and do a graceful shutdown with a bounded
`awaitTermination`. See `docs/ARCHITECTURE.md` for the full up-to-date
version of this reasoning.

## File handling & GUI split (built now vs. left for teammates)

- Built as part of the initial scaffold: `EventLogger` (file logging of
  every simulation event) and a working placeholder `MainFrame`
  (Start/Stop + live log), both wired through the `TrafficObserver`
  interface so neither the file logger nor the gui needed to touch
  engine internals.
- Left for Ayesha: the real `PersistenceService` implementation (CSV
  city-map loader/saver, snapshot save/resume, statistics export) - see
  `docs/TODO_Ayesha.md`.
- Left for Nameer: the real dashboard (`CityPanel`, `ControlPanel`,
  `StatisticsPanel` replacing the placeholder) - see
  `docs/TODO_Nameer.md`.

## Git workflow

Incremental, logically-scoped commits (scaffold -> exceptions -> observer
-> model -> engine -> persistence -> gui -> docs), pushed as each piece
landed rather than as one commit/push at the end - see `git log` for how
that actually played out.
