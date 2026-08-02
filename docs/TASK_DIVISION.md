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
| Repo owner | `model/`, `engine/`, `observer/` - the domain model (`Vehicle` hierarchy, `Road`, `Intersection`, `TrafficLight`, `CityMap`) and the concurrency core (`SimulationEngine` + its worker threads). Also the initial `EventLogger` file logging and the placeholder `MainFrame` that makes the app runnable end-to-end from day one. | OOP Principles, Concurrency & Synchronization, Logic & Efficiency |
| Ayesha Kamran | `persistence/` - real file handling: `CsvCityMapLoader` (implements `PersistenceService`), simulation snapshot save/resume, statistics CSV export. See `docs/TODO_Ayesha.md`. | File Handling, contributes to Logic & Efficiency |
| Nameer Ahmed | `gui/` - the real dashboard: `CityPanel` (painted map + animated vehicles + light indicators), `ControlPanel`, `StatisticsPanel`, replacing/extending the placeholder `MainFrame`. See `docs/TODO_Nameer.md`. | User Interface |

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
vertical slice first (engine runs, logs to a file, shows in a plain but
working window) so Ayesha and Nameer each start from a real, running
application and build their piece *on top of* it instead of *alongside*
it with integration risk saved for the last week.
