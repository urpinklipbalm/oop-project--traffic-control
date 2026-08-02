# Nameer's TODOs - GUI

You own the **User Interface** rubric category. The app already runs
end-to-end with a working-but-plain placeholder window
(`gui/MainFrame.java`: Start/Stop buttons + a scrolling text log, styled
with FlatLaf). Your job is to turn that into the real dashboard - you're
extending a working starting point, not building from a blank window.

Read `docs/ARCHITECTURE.md` first, specifically how `TrafficObserver`
works - it's how every panel you build gets live data without touching
any engine code.

**Important:** everything you implement as a `TrafficObserver` gets
called from background simulation threads, never the Swing EDT. Every
one of your `onXxx` methods that touches a Swing component **must**
wrap that touch in `SwingUtilities.invokeLater(...)`. Look at how
`MainFrame.appendLine()` currently does it - copy that pattern
everywhere. Skipping this doesn't crash immediately, it causes
intermittent Swing corruption/freezes that are miserable to debug later,
so get it right from the start.

## 1. `CityPanel` - the actual city visualization

New file: `src/main/java/com/trafficcontrol/gui/CityPanel.java`, a
`JPanel` with a custom `paintComponent(Graphics g)`.

- Draw each `Intersection` (from `engine.getCityMap().getIntersections()`)
  as a small square/circle at its `Position` (scale meters to pixels -
  the default grid is 0-360 on each axis, so multiplying by ~1.5-2x and
  adding some margin gives a reasonably sized window).
- Draw each `Road` as a line between its `from`/`to` intersections.
- Draw a colored dot per vehicle currently on a road
  (`Road.getVehiclesOnRoad()`), using `Vehicle.getColor()` - this is
  where the polymorphic `getColor()`/`getTypeName()` on each vehicle
  subtype actually gets used visually. To place the dot, call
  `vehicle.getProgressAlongRoad(System.currentTimeMillis(), engine.getClock().getSpeedFactor())`
  - it returns 0.0 at the road's start and 1.0 at its end, so you can
  lerp between the two intersections' positions.
- Color each intersection's light indicator based on
  `TrafficLight.getPhase()` (or subscribe to `onLightPhaseChanged` and
  cache the latest phase per intersection id - don't call into the
  engine's internals from the paint thread more than necessary).
- Call `repaint()` on a `javax.swing.Timer` (e.g. every 100-150ms) rather
  than repainting on every single observer callback - much smoother and
  avoids flooding the EDT.

## 2. `ControlPanel`

New file: `src/main/java/com/trafficcontrol/gui/ControlPanel.java`

- Start / Pause / Reset buttons (wired to `SimulationEngine`).
- A simulation speed slider - `engine.getClock().setSpeedFactor(...)`,
  valid range `SimulationClock.MIN_SPEED_FACTOR` to `MAX_SPEED_FACTOR`.
  It is safe to change while the simulation is running; the mover picks
  the new value up on its next tick.
- A "Spawn Emergency Vehicle" button - for now `VehicleSpawner` only
  spawns randomly; if you want an on-demand spawn, either add a small
  `spawnNow(Class<? extends Vehicle> type)`-style method to
  `VehicleSpawner` (keep it simple, ask before doing anything more
  invasive there) or just leave this as a stretch goal.
- Save / Load / Export buttons - wire these to Ayesha's
  `PersistenceService` implementation once it exists (`TODO_Ayesha.md`).
  Until then, disable them or leave them out.

## 3. `StatisticsPanel`

New file: `src/main/java/com/trafficcontrol/gui/StatisticsPanel.java`

- Live labels bound to `SimulationEngine.getStatistics()`
  (`SimulationStatistics`: vehicles spawned/arrived/in-transit, average
  wait time, average trip time). Poll it on the same `javax.swing.Timer`
  as `CityPanel`'s repaint, no need for a separate observer callback for
  this.
- A simple per-intersection congestion readout (`Intersection.getTotalQueueLength()`)
  is a nice touch - a small bar or colored label per intersection.

## 4. Replace `MainFrame`'s current layout

Swap the current `JScrollPane` log for a composed layout of
`CityPanel` (center), `ControlPanel` (north or west), `StatisticsPanel`
(east or south). You can keep the event log too (e.g. in a collapsible
panel or a separate tab) - it's genuinely useful for debugging - but it
shouldn't be the main view anymore.

## 5. Styling

FlatLaf is already set up (`FlatLightLaf.setup()` in `Main.java`) - you
get a clean modern look for free from any standard Swing component. A
few things worth doing on top of that:

- Consistent spacing/padding (`BorderFactory.createEmptyBorder(...)`).
- A small, consistent color palette for vehicle types (already defined
  per-class in `getColor()` - reuse those exact colors in any legend/key
  you add, don't invent new ones).
- Test resizing the window - nothing should clip or overlap.

## 6. Report section

Fill in the "User Interface" section of `docs/ProjectReport.md` (marked
with your name) - your layout choices, styling decisions, anything that
was tricky about painting/animating on a `JPanel`.

## Commit style

Small, incrementally-scoped commits with clear messages (see the
existing git log for the pattern this project follows) - not one giant
"added gui" commit. Push under your own GitHub account so the commit
history shows real per-person contribution.
