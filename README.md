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
| User Interface | done - live animated city map (`gui/CityPanel`) with moving vehicles, signal states, queues and emergency-preemption flashes, plus `MainFrame`'s Start/Stop, vehicle speed and traffic volume controls, manual/custom car spawning, and a File menu (load city / save+load snapshot / export statistics). The originally-planned separate `ControlPanel`/`StatisticsPanel` classes were absorbed into `MainFrame`'s toolbar and menu bar instead - see `ProjectReport.docx` section 9. **Nameer:** [`docs/TODO_Nameer.md`](docs/TODO_Nameer.md) |
| File Handling | done - `persistence.PersistenceService` implemented by `CsvCityMapLoader`: CSV city map load/save, statistics export, and snapshot save/resume, plus `EventLogger` (event log to disk). **Ayesha:** [`docs/TODO_Ayesha.md`](docs/TODO_Ayesha.md) |
| IDE & Version Control | done - small scoped commits, see `git log` |
| Ethics & Teamwork | done - [`docs/TASK_DIVISION.md`](docs/TASK_DIVISION.md); final report is [`ProjectReport.docx`](ProjectReport.docx) |

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
- `persistence/` - file handling: `EventLogger` (event log to disk) and
  `CsvCityMapLoader` (implements `PersistenceService` - CSV city map
  load/save, statistics export, snapshot save/resume)
- `gui/` - the Swing UI: `MainFrame` (window, toolbar, menu bar, and all
  dashboard controls) and `CityPanel` (the animated map)
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
