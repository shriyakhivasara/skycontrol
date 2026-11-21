# Class guide

Every class in SkyControl, grouped by folder (package), with what it does and the main OOP idea it shows.

## `model/`: the things at an airport

| Class | What it does | OOP concept |
|---|---|---|
| `Aircraft` | Abstract parent of all aircraft types. Declares `wakeCategory()`, `runwayOccupancyMinutes()`, `requiredGateSize()`, `turnaroundMinutes()`, `category()`. `Aircraft.of("A380")` builds the right subclass. | Abstract class, factory method |
| `Narrowbody` | A320, B737: medium wake, 1 min on the runway, normal gate, 45 min turnaround | Inheritance, overriding |
| `Widebody` | A380, B777, A350, B787, A330: heavy wake, 2 min, large gate, 90 min | Inheritance, overriding |
| `Cargo` | B747F, B777F: heavy, large gate, 120 min turnaround | Inheritance, overriding |
| `BusinessJet` | G650, CL350, E50P: light wake, normal gate, 30 min | Inheritance, overriding |
| `Flight` | One arrival or departure. Owns its fuel and priority: `burnFuel()` raises LOW_FUEL and throws `InsufficientFuelException` at the reserve. Validates itself when created. | Encapsulation, static factory methods |
| `FlightSnapshot` | Read-only copy of a flight at one moment, used by screens, database and reports | Record, immutability |
| `Runway` | Remembers the last aircraft that used it and works out when the next one may go | Encapsulation |
| `Gate` | A parking gate with a size and an occupant | Encapsulation |
| `Airport` | The runways and gates; `createDefault()` builds 2 runways and 14 gates | Composition |
| `SeparationRules` | Table of minimum gaps between aircraft by wake category | Utility class, 2D array |
| `SimTime` | Converts simulation minutes to clock times and back | Utility class |
| `FlightStatus` | SCHEDULED → APPROACHING → HOLDING → AT_GATE / REMOTE_STAND → LANDED; BOARDING → READY_FOR_DEPARTURE → DEPARTED; DIVERTED, CANCELLED | Enum with methods |
| `Priority` | NORMAL, LOW_FUEL, EMERGENCY, each with a rank | Enum with a field |
| `WakeCategory` | LIGHT, MEDIUM, HEAVY | Enum |
| `GateSize` | NORMAL, LARGE, with `fits()` | Enum with a method |
| `FlightType` | ARRIVAL, DEPARTURE | Enum |

## `policy/`: who goes first

| Class | What it does | OOP concept |
|---|---|---|
| `SchedulingPolicy` | Interface: gives a `Comparator<Flight>` that orders the runway queue | Interface, Strategy pattern |
| `FcfsPolicy` | First come, first served | Implements interface |
| `PriorityWithFuelPolicy` | Emergency first, then lowest fuel, then arrival order | Implements interface, chained comparators |
| `Policies` | Looks up a policy by its code | Factory |

## `engine/`: the simulation

| Class | What it does | OOP concept |
|---|---|---|
| `ControlTower` | Runs the simulation one minute at a time (`tick()`), applies events, logs everything and produces snapshots | Composition, `synchronized`, observer subject |
| `RunwayScheduler` | The runway queue (a `PriorityQueue`). Each minute clears the flight at the front onto the runway that can take it soonest. Policy can be swapped live. | Collections, Strategy, thread safety |
| `GateAllocator` | Gives each landed aircraft the smallest free gate that fits; throws `GateUnavailableException` if none | `EnumMap`, `HashMap`, custom exception |
| `SimClock` | Background thread that calls `tick()` at the chosen speed; pause, resume, stop | `Runnable`, `Thread`, `volatile` |
| `TrafficGenerator` | Adds unscheduled private jets and freighters using a seeded `Random` | Reproducible randomness |
| `FlightListener` | Interface for anything that wants to hear about flight events | Interface, Observer pattern |
| `TowerSnapshot` | Everything the screens need, copied at one moment | Record with nested records |
| `LogEntry` | One line of the tower log | Record |

## `events/`: things that happen during a run

| Class | What it does | OOP concept |
|---|---|---|
| `Event` | Interface: `apply(tower, now)` and `describe()` | Interface, Command pattern |
| `EmergencyEvent` | A flight declares MAYDAY | Implements interface |
| `RunwayClosureEvent` | Closes one runway for a time or until reopened | Implements interface |
| `RunwayReopenEvent` | Reopens a runway | Implements interface |
| `StormEvent` | Closes every runway for a while | Implements interface |
| `Events` | Short factory methods used by the buttons and the experiment | Factory |

## `exceptions/`

| Class | Thrown when | Result |
|---|---|---|
| `SkyControlException` | Parent of all the others | Catch one type for any SkyControl error |
| `RunwayClosedException` | A flight is cleared onto, or someone closes, a closed runway | Message on screen |
| `GateUnavailableException` | No free gate is big enough | Aircraft goes to a remote stand (+15 min turnaround) |
| `InsufficientFuelException` | A circling flight reaches its fuel reserve | Flight diverts to Abu Dhabi; its departure is cancelled |
| `InvalidFlightPlanException` | Bad flight number, unknown aircraft, too little fuel, departure before arrival, bad CSV line | Schedule refuses to load, naming the line |
| `InvalidEventException` | MAYDAY for a flight on the ground, unknown runway, reopening an open runway... | Message on screen |
| `StorageException` | The database cannot be opened, read or written | App falls back to memory |

## `data/`: files and the database

| Class | What it does | OOP concept |
|---|---|---|
| `ScheduleLoader` | Reads `schedule.csv`, skipping comments and the header; reports bad lines by number | File handling |
| `ScheduledFlight` | An arrival and the departure its aircraft becomes | Record |
| `Storage` | Interface for saving runs, flights and events | Interface, `AutoCloseable` |
| `SqliteStorage` | Storage in a SQLite file using JDBC and prepared statements | JDBC |
| `InMemoryStorage` | Storage in maps, used in tests and when the driver is missing | Polymorphism (drop-in replacement) |
| `AsyncStorageWriter` | Listens to the tower, queues each event and writes it to storage on a background thread | Producer-consumer, `BlockingQueue` |
| `RunSummary` | One past run as stored in the database | Record |

## `report/`

| Class | What it does | OOP concept |
|---|---|---|
| `Statistics` | Landings, delays, on-time %, diversions, runway use, movements per hour | Immutable class, `TreeMap` |
| `ReportExporter` | Writes flights and summary CSV files | File handling |

## `gui/`: the Swing screens

| Class | What it does | OOP concept |
|---|---|---|
| `MainWindow` | Lays out all panels; a Swing `Timer` refreshes them 4 times a second from one snapshot | `JFrame`, composition |
| `Refreshable` | Interface every live panel implements | Interface, polymorphism |
| `RadarPanel` | Draws the radar with Java 2D | Custom painting, `extends JPanel` |
| `FlightBoardPanel` | Arrivals or departures board | `JTable`, custom cell renderer |
| `FlightTableModel` | Table data for a board | `extends AbstractTableModel` |
| `GroundPanel` | Runway cards, queue table, gate tiles | Swing layouts |
| `StatsPanel` | Statistic tiles, hourly bar chart, saved runs | Custom painting, JDBC read |
| `ControlPanel` | MAYDAY, runway, storm and export buttons; shows exception messages | Event handling |
| `LogPanel` | The tower log | Swing |
| `Theme` | Shared colours and fonts; installs the dark Nimbus look | Constants, utility class |

## `experiment/`

| Class | What it does | OOP concept |
|---|---|---|
| `PolicyExperiment` | Runs the same day under both policies with 5 seeds on a thread pool, prints and saves a comparison | `ExecutorService`, `Future` |
| `ScenarioResult` | The outcome of one run | Record |

## `test/`

| Class | What it does |
|---|---|
| `Check` | Tiny test helper: counts passes and failures |
| `SchedulerTest` | Model, separation and scheduler tests (57 checks) |
| `SimulationTest` | Full simulation and event tests (65 checks) |
| `DataTest` | Loader, storage, background writer and export tests (42 checks) |
| `ConcurrencyTest` | Multi-thread stress test, clock thread, experiment repeatability (12 checks) |
| `ConsoleDemo` | Prints the runway scheduler at work, no GUI needed |
