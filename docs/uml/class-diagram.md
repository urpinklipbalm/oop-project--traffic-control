# Class Diagram

Renders directly in GitHub's markdown preview. For the submission zip
(which needs a PDF/image, not markdown), export this with the VS Code
"Markdown Preview Mermaid Support" extension (right-click the diagram ->
"Export as PNG/SVG"), or paste the mermaid source below into
[mermaid.live](https://mermaid.live) and export from there. `class-diagram.puml`
next to this file is the same diagram in PlantUML syntax, if your tool of
choice prefers that instead.

**Keep this updated as the design changes** — the project report needs
to describe how the UML evolved during coding, so if you deviate from
what's drawn here, note why in `docs/ProjectReport.md` rather than
silently diverging.

```mermaid
classDiagram
    direction LR

    class Vehicle {
        <<abstract>>
        -String id
        -String customLabel
        -List~Intersection~ route
        -int routeIndex
        -TravelState travelState
        -RoadPlacement placement
        -long cumulativeWaitMillis
        +getPriority() int*
        +getSpeedMetersPerSecond() double*
        +getColor() Color*
        +getTypeName() String*
        +getCustomLabel() String
        +advanceToNextIntersection()
        +isAtDestination() boolean
        +enterRoad(Road, long)
        +hasFinishedCurrentRoad(long, double) boolean
        +getProgressAlongRoad(long, double) double
        +startWaiting(long)
        +addWaitTime(long)
    }
    class Car
    class Bus
    class Motorcycle
    class EmergencyVehicle {
        +int PRIORITY$
    }
    Vehicle <|-- Car
    Vehicle <|-- Bus
    Vehicle <|-- Motorcycle
    Vehicle <|-- EmergencyVehicle

    class Direction {
        <<enumeration>>
        NORTH
        SOUTH
        EAST
        WEST
        +isNorthSouthAxis() boolean
        +opposite() Direction
    }

    class LightPhase {
        <<enumeration>>
        NS_GREEN
        NS_YELLOW
        EW_GREEN
        EW_YELLOW
        ALL_RED
        +isGreenFor(Direction) boolean
        +isYellowFor(Direction) boolean
    }

    class Position {
        -double x
        -double y
    }

    class Road {
        -String id
        -Intersection from
        -Intersection to
        -double lengthMeters
        -int lanes
        -Direction approachDirection
        -List~Vehicle~ vehiclesOnRoad
        +getTravelTimeMillis(double) long
        +getCongestionRatio() double
        +addVehicle(Vehicle)
        +removeVehicle(Vehicle)
    }

    class TrafficLight {
        <<Runnable>>
        -String intersectionId
        -LightPhase phase
        -boolean preemptRequested
        -long nsGreenMillis
        -long ewGreenMillis
        +run()
        +preempt(Direction)
        +isGreenFor(Direction) boolean
        +setNsGreenDurationMillis(long)
        +setEwGreenDurationMillis(long)
        +prepareForStart()
        +stop()
    }

    class Intersection {
        -String id
        -Position position
        -TrafficLight trafficLight
        -Map~Direction, Deque~Vehicle~~ queues
        -ReentrantLock queueLock
        +enqueue(Direction, Vehicle)
        +tryDequeue(Direction, long) Vehicle
        +getQueueLength(Direction) int
        +getQueuedVehicles(Direction) List~Vehicle~
        +getTotalQueueLength() int
    }

    class CityMap {
        -Map~String, Intersection~ intersections
        -Map~String, Map~String, Road~~ adjacency
        -List~Road~ roads
        +addIntersection(Intersection)
        +addRoad(Road)
        +getRoute(String, String) List~Intersection~
        +getRoad(String, String) Road
    }

    Intersection "1" *-- "1" TrafficLight : owns
    CityMap "1" *-- "many" Intersection : contains
    CityMap "1" *-- "many" Road : contains
    Road "many" --> "2" Intersection : from / to
    Vehicle "many" --> "many" Intersection : route
    Vehicle "0..1" --> "0..1" Road : currentRoad

    class TrafficEventPublisher {
        <<interface>>
        +publishVehicleSpawned(Vehicle)
        +publishVehicleRestored(Vehicle)
        +publishVehicleArrived(Vehicle, long, long)
        +publishLightPhaseChanged(String, LightPhase)
        +publishPreemption(String, Direction)
        +publishMessage(String)
    }

    class TrafficObserver {
        <<interface>>
        +onVehicleSpawned(Vehicle)
        +onVehicleRestored(Vehicle)
        +onVehicleArrived(Vehicle, long, long)
        +onLightPhaseChanged(String, LightPhase)
        +onPreemption(String, Direction)
        +onSimulationMessage(String)
    }

    class SimulationClock {
        -double speedFactor
        +getSpeedFactor() double
        +setSpeedFactor(double)
    }

    class TrafficVolume {
        <<enumeration>>
        LIGHT
        NORMAL
        BUSY
        RUSH_HOUR
        +getIntervalMultiplier() double
    }

    class SimulationEngine {
        -CityMap cityMap
        -SimulationStatistics statistics
        -SimulationClock clock
        -List~TrafficObserver~ observers
        -ExecutorService executor
        -AtomicBoolean running
        -TrafficVolume trafficVolume
        +start()
        +stop()
        +addObserver(TrafficObserver)
        +removeObserver(TrafficObserver)
        +setTrafficVolume(TrafficVolume)
        +spawnCar() Vehicle
        +spawnCar(String, String, String) Vehicle
        +restoreVehicles(List~SavedVehicle~)
    }
    SimulationEngine ..|> TrafficEventPublisher : implements
    SimulationEngine "1" o-- "many" TrafficObserver : notifies
    SimulationEngine "1" *-- "1" CityMap
    SimulationEngine "1" *-- "1" SimulationStatistics
    SimulationEngine "1" *-- "1" SimulationClock
    SimulationEngine "1" *-- "1" TrafficVolume : nested enum

    class VehicleSpawner {
        <<Runnable>>
        -double spawnIntervalMultiplier
        +run()
        +spawnCarNow() Vehicle
        +spawnCarNow(String, String, String) Vehicle
        +restoreVehicle(SavedVehicle) Vehicle
        +setSpawnIntervalMultiplier(double)
        +stop()
    }
    class VehicleMover {
        <<Runnable>>
        +run()
        +stop()
    }
    class AdaptiveSignalController {
        <<Runnable>>
        +run()
        +stop()
    }
    SimulationEngine "1" *-- "1" VehicleSpawner
    SimulationEngine "1" *-- "1" VehicleMover
    SimulationEngine "1" *-- "1" AdaptiveSignalController

    class SimulationStatistics {
        -AtomicLong vehiclesSpawned
        -AtomicLong vehiclesArrived
        -AtomicLong totalWaitTimeMillis
        -AtomicLong totalTravelTimeMillis
        +recordSpawn()
        +recordArrival(long, long)
        +restore(long, long, double, double)
        +getAverageWaitTimeMillis() double
        +getAverageTravelTimeMillis() double
    }

    class PersistenceService {
        <<interface>>
        +loadCityMap(String) CityMap
        +saveCityMap(CityMap, String)
        +exportStatistics(SimulationStatistics, String)
    }

    class CsvCityMapLoader {
        +loadCityMap(String) CityMap
        +saveCityMap(CityMap, String)
        +exportStatistics(SimulationStatistics, String)
        +loadSnapshot(String) SimulationSnapshot
        +saveSnapshot(CityMap, SimulationStatistics, String)
    }
    CsvCityMapLoader ..|> PersistenceService : implements
    CsvCityMapLoader ..> CityMap : creates
    CsvCityMapLoader ..> SimulationSnapshot : creates

    class SimulationSnapshot {
        -CityMap cityMap
        -long vehiclesSpawned
        -long vehiclesArrived
        -double averageWaitTimeMillis
        -double averageTravelTimeMillis
        -List~SavedVehicle~ unfinishedVehicles
        +getCityMap() CityMap
        +getUnfinishedVehicles() List~SavedVehicle~
    }
    class SavedVehicle {
        <<record>>
        +String type
        +String originId
        +String destinationId
        +String customLabel
    }
    SimulationSnapshot "1" *-- "many" SavedVehicle : nested record
    SimulationSnapshot "1" *-- "1" CityMap

    class EventLogger {
        -BufferedWriter writer
        +close()
    }
    EventLogger ..|> TrafficObserver : implements

    class DefaultCityMapFactory {
        <<utility, legacy>>
        +createDefaultGrid() CityMap$
    }
    DefaultCityMapFactory ..> CityMap : builds
    note for DefaultCityMapFactory "superseded by CsvCityMapLoader;\nkept for reference/tests only"

    class MainFrame {
        -SimulationEngine engine
        -CityPanel cityPanel
        -CsvCityMapLoader persistence
        -JTextArea eventLog
        +startSimulation()
        +stopSimulation()
        +spawnCar()
        +loadCityMap(Path)
        +saveSnapshot()
        +loadSnapshot()
        +exportStatistics()
    }
    MainFrame ..|> TrafficObserver : implements
    MainFrame --> SimulationEngine : controls
    MainFrame ..> CsvCityMapLoader : uses

    class CityPanel {
        -SimulationEngine engine
        -Map~Intersection, Map~Direction, Road~~ incomingRoads
        -Map~String, Long~ lastPreemption
        -Map~String, Vehicle~ labeledVehicles
        +paintComponent(Graphics)
        +disposePanel()
    }
    CityPanel ..|> TrafficObserver : implements
    MainFrame "1" *-- "1" CityPanel

    class InvalidRouteException {
        <<checked>>
    }
    class CityMapLoadException {
        <<checked>>
    }
    class SimulationStateException {
        <<unchecked>>
    }
```
