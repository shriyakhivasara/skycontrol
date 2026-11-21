# SkyControl: build plan

Air traffic control and airport operations simulator. Java OOP course project, team of 3.

## Stages

| Stage | What was built | Status |
|---|---|---|
| 1. Skeleton and model | Aircraft hierarchy (4 types), Flight, Runway, Gate, Airport, enums, custom exceptions | Done |
| 2. Runway engine | `RunwayScheduler` (PriorityQueue), wake separation rules, FCFS and priority policies | Done |
| 3. Simulation | `ControlTower`, `SimClock` thread, fuel burn and LOW FUEL, `GateAllocator` with remote stands, turnaround into departures, `TrafficGenerator` | Done |
| 4. Events | MAYDAY, runway closure and reopen, thunderstorm, diversions and cancelled departures | Done |
| 5. Data | `schedule.csv` loader, SQLite storage (JDBC), background writer thread, CSV reports | Done |
| 6. Screens | Swing GUI: radar, arrivals and departures boards, runways and gates, statistics, event controls, tower log | Done |
| 7. Integration and experiment | Run scripts, FCFS vs priority experiment on a thread pool, concurrency stress test, README, class guide, glossary | Done |

## Suggested split for the report and viva

Each person should be able to explain the whole system, but owns one area in depth:

- **Person A, engine:** `model/`, `policy/`, `RunwayScheduler`, `GateAllocator`, exceptions, `SchedulerTest`
- **Person B, screens:** `gui/`, `Main`, demo script, screenshots
- **Person C, simulation and data:** `ControlTower`, `SimClock`, `events/`, `data/`, `report/`, `experiment/`, report lead

## Report outline

1. Problem and motivation
2. Class diagram and design decisions (see `docs/CLASSES.md`)
3. OOP concept mapping (table in `README.md`)
4. Experiment: FCFS vs priority (table in `README.md`, data in `data/exports/experiment_results.csv`)
5. Screenshots (`docs/screenshots/`)
6. Testing (176 checks in 4 suites)
7. Limitations and future work
8. Glossary (`docs/GLOSSARY.md`)
