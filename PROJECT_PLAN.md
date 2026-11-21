# SkyControl: build plan

Air traffic control and airport operations simulator. Java OOP course project, team of 3, 9 days.

## Stages

| Stage | What gets built | Done when | Status |
|---|---|---|---|
| 1. Skeleton and model | Aircraft hierarchy, Flight, Runway, Gate, Airport, enums, exceptions, API contract | Classes compile, `docs/API.md` written | Done |
| 2. Runway engine | `RunwayScheduler` (PriorityQueue), separation rules, FCFS and priority policies, tests | MAYDAY jumps the queue; correct gaps behind heavy aircraft | Done |
| 3. Simulation | `SimClock` thread, fuel burn, automatic LOW FUEL, `GateAllocator`, `TrafficGenerator`, turnaround into departures | 2 simulated hours run with no crash; gates never double-booked | |
| 4. Events | Emergency, runway closure, weather delay, diversion | Closing a runway reroutes traffic; a low-fuel flight diverts | |
| 5. Data | `schedule.csv` loader, SQLite storage, report, CSV export | History survives a restart; export opens in Excel | |
| 6. Web screens | Built-in `HttpServer`, API, board, dashboard, radar | Phone shows the board, laptop shows the radar, both live | |
| 7. Integration and experiment | Run scripts, FCFS vs PriorityWithFuel comparison, README, CLASSES.md | One command runs everything; results table ready | |

Day 9: code freeze. Report, slides, viva practice only.

## Team split

- **Person A (engine):** Aircraft hierarchy, RunwayScheduler, GateAllocator, policies, exceptions, tests
- **Person B (screens):** radar, arrivals/departures board, dashboard, live updates
- **Person C (simulation and data):** SimClock, TrafficGenerator, schedule CSV, SQLite, reports, report lead

## Scope cap

2 runways, 10 gates (6 normal, 4 large), 4 aircraft types, 3 events. Nothing more unless Day 7 finishes early.

## Rules

- `engine/` never imports anything from `web/`, so the console demo always works as a fallback.
- One commit per stage with a clear message.
- Checkpoints: scheduler console demo by end of Day 3, full flow in the browser by end of Day 6.
