# Nameer's TODOs - GUI

You own the **User Interface** rubric category. The window already has
Start/Stop controls, the animated city map (`gui/CityPanel.java`), and a
live event log, all styled with FlatLaf. The two panels that surround
the map are yours to build, plus the styling pass over the whole thing -
you're extending a working app, not starting from a blank window.

Read `docs/ARCHITECTURE.md` first, specifically how `TrafficObserver`
works - it's how every panel you build gets live data without touching
any engine code. `CityPanel` is a worked example of the pattern: it
subscribes for preemption events, reads everything else straight off the
model on each repaint, and never writes anything back.

**Important:** everything you implement as a `TrafficObserver` gets
called from background simulation threads, never the Swing EDT. Every
one of your `onXxx` methods that touches a Swing component **must**
wrap that touch in `SwingUtilities.invokeLater(...)`. Look at how
`MainFrame.appendLine()` currently does it - copy that pattern
everywhere. Skipping this doesn't crash immediately, it causes
intermittent Swing corruption/freezes that are miserable to debug later,
so get it right from the start.

## 1. `CityPanel` - DONE, but worth reading before you start

`gui/CityPanel.java` already draws the live map: vehicles animating
along their roads, coloured stop lines per approach, queues tailing back
from red lights, a fading ring on any junction an emergency vehicle just
preempted, and a legend. It repaints on a ~30fps `javax.swing.Timer`.

You don't need to rebuild it, but do read it - it's the template for the
panels below, and there are two things in it worth copying:

- It repaints on a timer rather than on every observer callback, which
  keeps the animation smooth instead of flooding the EDT.
- It scales the layout from the actual intersection positions, so it
  still lays out correctly when Ayesha's loader supplies a different map.

If you want to extend it, obvious candidates: hovering a vehicle to show
its route, or shading each road by `Road.getCongestionRatio()`.

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

## 4. Fit your panels into `MainFrame`

`MainFrame` currently splits vertically: the map on top, the event log
below. Add `ControlPanel` and `StatisticsPanel` around that - west/east
of the map, or a toolbar strip and a side column, whichever you think
reads better. Keep the event log; it's genuinely useful when
demonstrating, just don't let it take space from the map.

## 5. Styling

FlatLaf is already set up (`FlatLightLaf.setup()` in `Main.java`) - you
get a clean modern look for free from any standard Swing component. A
few things worth doing on top of that:

- Consistent spacing/padding (`BorderFactory.createEmptyBorder(...)`).
- Reuse the existing palette. Vehicle colours are defined per-class in
  `getColor()` and `CityPanel` already draws a legend from them - don't
  invent new ones for your panels.
- Test resizing the window - nothing should clip or overlap.
- Optional but a strong finish: a light/dark theme toggle. FlatLaf makes
  this a one-liner (`FlatDarkLaf.setup()`), but `CityPanel`'s colours are
  currently constants tuned for the light theme, so you'd need to pull
  them out into a small theme object for the map to follow along.

## 6. Report section

Fill in the "User Interface" section of `docs/ProjectReport.md` (marked
with your name) - your layout choices, styling decisions, anything that
was tricky about painting/animating on a `JPanel`.

## Commit style

Small, incrementally-scoped commits with clear messages (see the
existing git log for the pattern this project follows) - not one giant
"added gui" commit. Push under your own GitHub account so the commit
history shows real per-person contribution.
