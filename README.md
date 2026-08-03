# Smart City Traffic Control

CS-212 (Object Oriented Programming, Summer 2026) final group project.
A multithreaded traffic simulation for a small smart city grid, with a
Swing desktop GUI, file-based persistence, and Java concurrency
throughout - see the [project description](.) for the full assignment
brief.

**Team:** repo owner, [Ayesha Kamran](https://github.com/Ayesha-Kamran),
[Nameer Ahmed](https://github.com/nameer451).

## What this is

Vehicles (cars, buses, motorcycles, and priority emergency vehicles)
spawn at random points in a 3×3 grid of intersections, route themselves
across the city, and move through it obeying traffic lights that run
independently and adapt their timing to real queue congestion. Every
intersection's light, the vehicle spawner, the vehicle mover, and the
adaptive signal controller all run as separate threads coordinating over
shared state through explicit locks and atomics - see
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design and
why it's built this way.

## Status

| Rubric area | Status |
|---|---|
| UML Design | done - [`docs/uml/class-diagram.md`](docs/uml/class-diagram.md) (Mermaid) / `.puml` (PlantUML); keep in sync as the design evolves |
| OOP Principles | done - abstract `Vehicle` + 4 subclasses, interfaces (`TrafficObserver`, `TrafficEventPublisher`, `PersistenceService`), encapsulated shared state throughout `model/` |
| Concurrency & Synchronization | done - see `engine/` and `model/TrafficLight.java` / `model/Intersection.java` |
| Logic & Efficiency | done - BFS routing (`CityMap.getRoute`), load-adaptive signal timing (`AdaptiveSignalController`) |
| User Interface | partial - live animated city map (`gui/CityPanel`) with moving vehicles, signal states, queues and emergency-preemption flashes, plus Start/Stop and an event log. Control and statistics panels still to come. **Nameer:** [`docs/TODO_Nameer.md`](docs/TODO_Nameer.md) |
| File Handling | partial - `EventLogger` (event log to disk) is done; CSV map loading, snapshots, stats export are not. **Ayesha:** [`docs/TODO_Ayesha.md`](docs/TODO_Ayesha.md) |
| IDE & Version Control | ongoing - small scoped commits, see `git log` |
| Ethics & Teamwork | ongoing - [`docs/TASK_DIVISION.md`](docs/TASK_DIVISION.md), fill in [`docs/ProjectReport.md`](docs/ProjectReport.md) as you go, don't leave it to the last night |

## Building and running

Requires JDK 21+ and Maven.

```bash
mvn compile        # build
mvn exec:java       # run
mvn package          # produces target/traffic-control.jar (runnable: java -jar target/traffic-control.jar)
```

On first run Maven downloads FlatLaf and a couple of build plugins - if
that seems to hang, it's almost certainly just a slow connection to
Maven Central, not a broken build.

## Repository layout

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full
breakdown. Short version:

- `model/` - domain objects (`Vehicle` hierarchy, `Road`, `Intersection`,
  `TrafficLight`, `CityMap`)
- `engine/` - `SimulationEngine` and its worker threads
- `observer/` - the `TrafficObserver` / `TrafficEventPublisher`
  interfaces everything else plugs into
- `persistence/` - file handling (`EventLogger` is done; the real
  CSV-backed `PersistenceService` implementation is Ayesha's part)
- `gui/` - the Swing UI: `MainFrame` (window + controls) and `CityPanel`
  (the animated map). The control and statistics panels are Nameer's part
- `exceptions/` - custom checked/unchecked exceptions
- `docs/` - architecture notes, UML, task lists, the project report

## Working on this repo

- **Read your TODO doc first** ([Ayesha's](docs/TODO_Ayesha.md) /
  [Nameer's](docs/TODO_Nameer.md)) - it names the exact files to add and
  the interface to implement against.
- **Commit small and often.** One logical change per commit, a message
  that says *why*, not just *what*. Push under your own GitHub account -
  the commit history is part of the grade.
- **Comments explain the non-obvious.** If a comment just restates what
  the code already says, it shouldn't be there - write comments for the
  *why* (a constraint, a tradeoff, a gotcha), not the *what*.
- **Update `docs/ProjectReport.md` as you go**, not the night before
  submission - the "how the UML evolved during coding" question is much
  easier to answer if you wrote down what changed *when* it changed.

## Collaborators

If you're reading this and don't have push access yet, ask the repo
owner for a GitHub collaborator invite (Settings -> Collaborators on the
repo).
