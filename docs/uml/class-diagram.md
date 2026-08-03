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
        -List~Intersection~ route
        -int routeIndex
        -TravelState travelState
        -Road currentRoad
        +getPriority() int*
        +getSpeedMetersPerSecond() double*
        +getColor() Color*
        +getTypeName() String*
        +advanceToNextIntersection()
        +isAtDestination() boolean
        +enterRoad(Road, long)
        +hasFinishedCurrentRoad(long) boolean
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
    }

    class TrafficLight {
        <<Runnable>>
        -String intersectionId
        -LightPhase phase
        -boolean preemptRequested
        +run()
        +preempt(Direction)
        +isGreenFor(Direction) boolean
        +setNsGreenDurationMillis(long)
        +setEwGreenDurationMillis(long)
    }

    class Intersection {
        -String id
        -Position position
        -TrafficLight trafficLight
        -Map~Direction, Deque~Vehicle~~ queues
        +enqueue(Direction, Vehicle)
        +tryDequeue(Direction) Vehicle
        +getQueueLength(Direction) int
    }

    class CityMap {
        -Map~String, Intersection~ intersections
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
        +publishVehicleArrived(Vehicle, long, long)
        +publishLightPhaseChanged(String, LightPhase)
        +publishPreemption(String, Direction)
        +publishMessage(String)
    }

    class TrafficObserver {
        <<interface>>
        +onVehicleSpawned(Vehicle)
        +onVehicleArrived(Vehicle, long, long)
        +onLightPhaseChanged(String, LightPhase)
        +onPreemption(String, Direction)
        +onSimulationMessage(String)
    }

    class SimulationEngine {
        -CityMap cityMap
        -SimulationStatistics statistics
        -List~TrafficObserver~ observers
        -ExecutorService executor
        +start()
        +stop()
        +addObserver(TrafficObserver)
    }
    SimulationEngine ..|> TrafficEventPublisher : implements
    SimulationEngine "1" o-- "many" TrafficObserver : notifies
    SimulationEngine "1" *-- "1" CityMap
    SimulationEngine "1" *-- "1" SimulationStatistics

    class VehicleSpawner {
        <<Runnable>>
        +run()
    }
    class VehicleMover {
        <<Runnable>>
        +run()
    }
    class AdaptiveSignalController {
        <<Runnable>>
        +run()
    }
    SimulationEngine "1" *-- "1" VehicleSpawner
    SimulationEngine "1" *-- "1" VehicleMover
    SimulationEngine "1" *-- "1" AdaptiveSignalController

    class SimulationStatistics {
        -AtomicLong vehiclesSpawned
        -AtomicLong vehiclesArrived
        -AtomicLong totalWaitTimeMillis
        +recordSpawn()
        +recordArrival(long, long)
        +getAverageWaitTimeMillis() double
    }

    class PersistenceService {
        <<interface>>
        +loadCityMap(String) CityMap
        +saveCityMap(CityMap, String)
        +exportStatistics(SimulationStatistics, String)
    }

    class EventLogger {
        -BufferedWriter writer
        +close()
    }
    EventLogger ..|> TrafficObserver : implements

    class DefaultCityMapFactory {
        <<utility>>
        +createDefaultGrid() CityMap$
    }
    DefaultCityMapFactory ..> CityMap : builds

    class MainFrame {
        -SimulationEngine engine
        -CityPanel cityPanel
        -JTextArea eventLog
    }
    MainFrame ..|> TrafficObserver : implements
    MainFrame --> SimulationEngine : controls

    class CityPanel {
        -SimulationEngine engine
        -Map~Intersection, Map~Direction, Road~~ incomingRoads
        -Map~String, Long~ lastPreemption
        +paintComponent(Graphics)
    }
    CityPanel ..|> TrafficObserver : implements
    MainFrame "1" *-- "1" CityPanel

    class InvalidRouteException
    class CityMapLoadException
    class SimulationStateException
```
