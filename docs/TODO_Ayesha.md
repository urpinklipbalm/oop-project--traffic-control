# Ayesha's TODOs - File Handling & Persistence

You own the **File Handling** rubric category (and part of Logic &
Efficiency, via statistics). The engine, model, and a placeholder gui
already run end-to-end - `EventLogger` (in `persistence/`) already gives
the project basic file handling (a live text log of every simulation
event), so you're not starting from zero, you're building the *real*
persistence layer on top of that foundation.

Read `docs/ARCHITECTURE.md` first (the "integration seams" section
especially) - it explains the interface you're implementing and why.

## 1. `CsvCityMapLoader implements PersistenceService`

New file: `src/main/java/com/trafficcontrol/persistence/CsvCityMapLoader.java`

- Parse `src/main/resources/maps/default-city.csv` - the exact format is
  documented in comments at the top of that file (two sections,
  `[INTERSECTIONS]` and `[ROADS]`, `#` for comments).
- `loadCityMap(String filePath)`: build a `CityMap` the same way
  `DefaultCityMapFactory.createDefaultGrid()` does (look at that class -
  ~50 lines, it's the reference for what a correctly-wired `CityMap`
  looks like), but reading rows from the file instead of a hardcoded
  loop.
- Throw `CityMapLoadException` (in `exceptions/`) for: missing file,
  malformed row (wrong column count, non-numeric x/y/length/lanes,
  unknown `approachDirection` value), or a road referencing an
  intersection id that doesn't exist. Don't let a bad file half-build a
  `CityMap` and return it - fail cleanly.
- `saveCityMap(CityMap cityMap, String filePath)`: write the same format
  back out. Handy for a "Save Map As..." feature if Nameer wants one.
- Add 1-2 more sample `.csv` files under `resources/maps/` with a
  different layout (doesn't have to be a grid - a few intersections in
  an irregular pattern is fine, `CityMap.getRoute()`'s BFS doesn't care
  about geometry) to show the loader handles more than just the one
  shape.

## 2. Wire it into `Main.java`

In `src/main/java/com/trafficcontrol/Main.java`, there's a line marked:

```java
// TODO(Ayesha): once persistence.PersistenceService has a real CSV loader, load
// src/main/resources/maps/default-city.csv through it instead of this hardcoded grid.
CityMap cityMap = DefaultCityMapFactory.createDefaultGrid();
```

Replace it with your loader once it works. That's the only change
needed in `Main` - everything downstream (engine, gui) just consumes
whatever `CityMap` it's handed.

## 3. Statistics export

`exportStatistics(SimulationStatistics statistics, String filePath)` -
write a CSV report (vehicles spawned/arrived, average wait time, average
trip time - see the getters on `SimulationStatistics` in `engine/`).
Coordinate with Nameer on an "Export Report" button in his
`ControlPanel` that calls this.

## 4. Simulation snapshot save/resume (stretch goal, do this last)

Save enough state to a file that a simulation can be paused and later
resumed roughly where it left off - at minimum, the city map plus
`SimulationStatistics`'s current totals. Full vehicle-position resume is
a nice-to-have, not required. Document whatever format/tradeoffs you
pick in `docs/ProjectReport.md`'s File Handling section - "why CSV vs.
Java serialization vs. something else" is exactly the kind of design
reasoning that section is asking for.

## 5. Report section

Once the above is done, fill in the "File Handling" section of
`docs/ProjectReport.md` (marked with your name) - what you built, why,
what broke while building it, how you handle a corrupted/missing file.

## Commit style

Small, incrementally-scoped commits with clear messages (see the
existing git log for the pattern this project follows) - not one giant
"added persistence" commit. Push under your own GitHub account so the
commit history shows real per-person contribution.
