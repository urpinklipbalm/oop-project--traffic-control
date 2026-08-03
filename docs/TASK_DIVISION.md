# Task Division

CS-212 final project, domain: Traffic Control in a Smart City. Group of
3: repo owner, **Ayesha Kamran** (`@Ayesha-Kamran`), **Nameer Ahmed**
(`@nameer451`).

The project was split along architectural seams rather than arbitrary
file lists, so each person owns a coherent, independently testable slice
and nobody needs to wait on anyone else to start. `docs/ARCHITECTURE.md`
explains the two interfaces (`TrafficObserver`, `PersistenceService`)
that make this possible.

| Person | Owns | Primary rubric categories |
|---|---|---|
| Repo owner | `model/`, `engine/`, `observer/` - the domain model (`Vehicle` hierarchy, `Road`, `Intersection`, `TrafficLight`, `CityMap`) and the concurrency core (`SimulationEngine` + its worker threads). Also `EventLogger` file logging, and `gui/CityPanel` - the animated map that makes the simulation visible. | OOP Principles, Concurrency & Synchronization, Logic & Efficiency |
| Ayesha Kamran | `persistence/` - real file handling: `CsvCityMapLoader` (implements `PersistenceService`), simulation snapshot save/resume, statistics CSV export. See `docs/TODO_Ayesha.md`. | File Handling, contributes to Logic & Efficiency |
| Nameer Ahmed | `gui/` - `ControlPanel` (speed slider, spawn controls, save/load/export wired to Ayesha's persistence), `StatisticsPanel` (live throughput, wait times, per-junction congestion), the `MainFrame` layout around them, and the styling/theming pass over the whole window. See `docs/TODO_Nameer.md`. | User Interface |

Everyone contributes to: **UML Design** (the diagram is shared and
should be kept in sync with whatever changes during implementation -
note deviations in the Project Report), **IDE & Version Control** (small
incremental commits, pushed under your own account), and **Ethics &
Teamwork** (the Project Report's task-division section should reflect
this table plus anything that changed in practice).

## Why this split and not a 3-way file-count split

A traffic simulation is naturally layered: something has to *model and
run* the simulation before there's anything to *save* or *show*. Rather
than divide files arbitrarily and force everyone to stub out
placeholders for parts they don't own, the repo owner built a complete
vertical slice first - engine running, logging to file, and the animated
map that makes it visible - so Ayesha and Nameer each start from a real,
working application and build their piece *on top of* it instead of
*alongside* it with all the integration risk saved for the last week.

The map ended up with the repo owner rather than Nameer because it turns
on the same concurrency details as the engine: reading live simulation
state from the Swing thread while nine light threads and the mover are
writing it. Finding and fixing that race (see commit `ad384b0`) belonged
with the concurrency work. Nameer's share stayed the same size - the two
surrounding panels, the window layout, and the styling pass - and those
are the parts a viewer actually interacts with.
