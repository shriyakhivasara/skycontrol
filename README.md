# SkyControl

An air traffic control and airport operations simulator written in Java for the Object Oriented Programming course project (BITS Pilani, Dubai Campus).

Flights approach a simulated two-runway airport on a sped-up clock. The system decides who lands and takes off next, keeps safe gaps between aircraft, assigns gates, and reacts live to emergencies, low fuel and runway closures.

Airline names and aircraft types are real; all flights, times and events are simulated.

> Work in progress. See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the stages.

## Run the tests

You need JDK 17 or newer.

| System | Command |
|---|---|
| Windows | `run-tests.bat` |
| macOS / Linux | `./run-tests.sh` |

Console walkthrough of the runway scheduler:

```
javac -d out $(find src -name '*.java')
java -cp out test.ConsoleDemo
```
