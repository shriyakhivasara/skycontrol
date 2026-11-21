package test;

import engine.ControlTower;
import engine.GateAllocator;
import engine.TowerSnapshot;
import engine.TrafficGenerator;
import events.Events;
import exceptions.GateUnavailableException;
import exceptions.InvalidEventException;
import exceptions.InvalidFlightPlanException;
import exceptions.RunwayClosedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import model.Aircraft;
import model.Airport;
import model.Flight;
import model.FlightStatus;
import model.Gate;
import model.GateSize;
import model.Priority;
import policy.FcfsPolicy;
import policy.PriorityWithFuelPolicy;
import report.Statistics;

/** Tests for the full simulation: life cycle, gates, fuel, diversions and events. */
public class SimulationTest {

    public static void main(String[] args) throws Exception {
        Check c = new Check("SimulationTest");
        lifeCycle(c);
        earlyTurnaroundWaits(c);
        gateAllocation(c);
        remoteStand(c);
        lowFuelEscalation(c);
        stormCausesDiversion(c);
        priorityPolicySavesFuel(c);
        emergencyEvent(c);
        runwayEvents(c);
        flightPlanChecks(c);
        generator(c);
        statistics(c);
        System.exit(c.finish() == 0 ? 0 : 1);
    }

    static ControlTower tower() {
        return new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(), null);
    }

    static void runTo(ControlTower t, int minute) {
        while (t.getMinute() < minute) {
            t.tick();
        }
    }

    static Flight arr(String no, String model, int time, double fuel) throws Exception {
        return Flight.arrival(no, "Test Air", "BOM", Aircraft.of(model), time, fuel);
    }

    static Flight dep(String no, String model, int time) throws Exception {
        return Flight.departure(no, "Test Air", "LHR", Aircraft.of(model), time);
    }

    static void lifeCycle(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("EK500", "A320", 40, 60);
        Flight d = dep("EK501", "A320", 40 + 45 + 15);
        t.addFlight(a, d);

        runTo(t, 9);
        c.equal(FlightStatus.SCHEDULED, a.getStatus(), "before minute 10 the arrival is not on radar");
        runTo(t, 10);
        c.equal(FlightStatus.APPROACHING, a.getStatus(), "30 min before arrival it appears on radar");
        runTo(t, 39);
        c.equal(31.0, a.getFuelMinutes(), "fuel burns 1 per minute while approaching (60 -> 31)");
        runTo(t, 40);
        c.equal(FlightStatus.AT_GATE, a.getStatus(), "with an empty airport it lands on time and goes to a gate");
        c.equal(0, a.delayMinutes(), "arrival delay is 0");
        c.that(a.getGateId() != null && a.getGateId().startsWith("B"), "A320 is given a normal gate (B...)");
        c.equal(FlightStatus.BOARDING, d.getStatus(), "its departure starts boarding");
        runTo(t, 84);
        c.equal(FlightStatus.AT_GATE, a.getStatus(), "still at the gate during turnaround");
        runTo(t, 90);
        c.equal(FlightStatus.LANDED, a.getStatus(), "after turnaround the arrival is complete");
        c.equal(FlightStatus.DEPARTED, d.getStatus(), "and the departure has taken off");
        c.equal(0, d.delayMinutes(), "departure left on time");
        c.that(t.allFinished(), "simulation reports every flight finished");
        long freeGates = t.snapshot().gates().stream().filter(TowerSnapshot.GateStatus::isFree).count();
        c.equal(14L, freeGates, "all 14 gates are free again");
    }

    static void earlyTurnaroundWaits(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("QR300", "A320", 30, 60);
        Flight d = dep("QR301", "A320", 200);
        t.addFlight(a, d);
        runTo(t, 120);
        c.equal(FlightStatus.AT_GATE, a.getStatus(), "aircraft ready early waits at the gate for its departure time");
        runTo(t, 191);
        c.equal(FlightStatus.DEPARTED, d.getStatus(), "it pushes back up to 10 min early and leaves");
    }

    static void gateAllocation(Check c) throws Exception {
        List<Gate> gs = new ArrayList<>();
        gs.add(new Gate("B1", GateSize.NORMAL));
        gs.add(new Gate("A1", GateSize.LARGE));
        GateAllocator g = new GateAllocator(gs);
        Flight small1 = arr("FZ1", "B737", 0, 60);
        Flight small2 = arr("FZ2", "B737", 0, 60);
        Flight big = arr("EK1", "A380", 0, 60);
        c.equal("B1", g.allocate(small1).getId(), "narrowbody takes the normal gate first");
        c.equal("A1", g.allocate(small2).getId(), "second narrowbody overflows to the large gate");
        try {
            g.allocate(big);
            c.that(false, "A380 with no large gate free throws GateUnavailableException");
        } catch (GateUnavailableException e) {
            c.that(true, "A380 with no large gate free throws GateUnavailableException");
        }
        g.release(small2);
        c.equal("A1", g.allocate(big).getId(), "after release the A380 gets A1");
        c.equal(0, g.freeGates(GateSize.LARGE), "no large gates left");
        c.equal(0, g.freeGates(GateSize.NORMAL), "no normal gates left");
    }

    static void remoteStand(Check c) throws Exception {
        Airport small = new Airport("TST", "Test");
        small.addRunway(new model.Runway("09"));
        small.addGate(new Gate("A1", GateSize.LARGE));
        ControlTower t = new ControlTower(small, new PriorityWithFuelPolicy(), null);
        Flight a1 = arr("EK10", "A380", 30, 60);
        Flight a2 = arr("EK12", "B777", 31, 60);
        Flight d2 = dep("EK13", "B777", 31 + 90 + 15);
        t.addFlight(a1, null);
        t.addFlight(a2, d2);
        runTo(t, 35);
        c.equal(FlightStatus.REMOTE_STAND, a2.getStatus(), "second widebody goes to a remote stand when the only gate is taken");
        c.equal("Remote", a2.getGateId(), "its gate shows Remote");
        int ready = a2.getActualTime() + 90 + GateAllocator.REMOTE_STAND_PENALTY;
        runTo(t, ready - 1);
        c.that(d2.getStatus() != FlightStatus.DEPARTED, "remote stand turnaround takes 15 min longer");
        runTo(t, ready + 3);
        c.equal(FlightStatus.DEPARTED, d2.getStatus(), "then it departs");
    }

    static void lowFuelEscalation(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("AI900", "A320", 30, 45);      // will reach the airport with 15 min
        t.addFlight(a, null);
        t.closeRunway("12L", -1);
        t.closeRunway("12R", -1);
        runTo(t, 30);
        c.equal(Priority.LOW_FUEL, a.getPriority(), "flight becomes LOW_FUEL once under 20 min");
        c.equal(FlightStatus.HOLDING, a.getStatus(), "and is holding because runways are closed");
    }

    static void stormCausesDiversion(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("AI901", "A320", 30, 45);
        Flight d = dep("AI902", "A320", 100);
        t.addFlight(a, d);
        runTo(t, 25);
        t.fireEvent(Events.storm(30));
        runTo(t, 40);
        c.equal(FlightStatus.DIVERTED, a.getStatus(), "with runways closed by a storm, the low-fuel flight diverts");
        c.equal(FlightStatus.CANCELLED, d.getStatus(), "its return departure is cancelled");
        runTo(t, 56);
        c.that(t.snapshot().runways().stream().allMatch(TowerSnapshot.RunwayStatus::open), "runways reopen when the storm ends");
        boolean logged = t.snapshot().log().stream().anyMatch(e -> e.message().contains("THUNDERSTORM"));
        c.that(logged, "storm is written to the log");
    }

    /** Same traffic, one runway, both policies: the priority policy should divert fewer flights. */
    static void priorityPolicySavesFuel(Check c) throws Exception {
        int[] diversions = new int[2];
        policy.SchedulingPolicy[] ps = {new FcfsPolicy(), new PriorityWithFuelPolicy()};
        for (int p = 0; p < 2; p++) {
            Airport one = new Airport("TST", "Test");
            one.addRunway(new model.Runway("09"));
            for (int i = 1; i <= 20; i++) {
                one.addGate(new Gate("G" + i, GateSize.LARGE));
            }
            ControlTower t = new ControlTower(one, ps[p], null);
            for (int i = 0; i < 12; i++) {
                double fuel = i >= 9 ? 44 : 80;   // the last three to arrive are short of fuel
                t.addFlight(arr("TS" + (100 + i), "A380", 30, fuel), null);
            }
            runTo(t, 60);
            diversions[p] = t.statistics().getDiversions();
        }
        c.that(diversions[0] > 0, "FCFS makes low-fuel flights wait and some divert (" + diversions[0] + ")");
        c.equal(0, diversions[1], "priority policy lands them first, no diversions");
    }

    static void emergencyEvent(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("FZ400", "B737", 40, 60);
        t.addFlight(a, null);
        try {
            t.fireEvent(Events.emergency("FZ400"));
            c.that(false, "MAYDAY for a flight not yet on radar is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "MAYDAY for a flight not yet on radar is rejected");
        }
        runTo(t, 15);
        t.fireEvent(Events.emergency("FZ400"));
        c.equal(Priority.EMERGENCY, a.getPriority(), "MAYDAY sets EMERGENCY priority");
        c.equal(FlightStatus.HOLDING, a.getStatus(), "an approaching MAYDAY flight comes straight in");
        runTo(t, 16);
        c.that(a.getActualTime() == 16, "and lands at the next minute, well before its schedule");
        try {
            t.fireEvent(Events.emergency("XX999"));
            c.that(false, "MAYDAY for an unknown flight is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "MAYDAY for an unknown flight is rejected");
        }
    }

    static void runwayEvents(Check c) throws Exception {
        ControlTower t = tower();
        runTo(t, 5);
        t.fireEvent(Events.closeRunway("12R", 10));
        c.that(!t.getAirport().getRunway("12R").isOpen(), "runway closure closes 12R");
        try {
            t.fireEvent(Events.closeRunway("12R", 10));
            c.that(false, "closing it again throws RunwayClosedException");
        } catch (RunwayClosedException e) {
            c.that(true, "closing it again throws RunwayClosedException");
        }
        runTo(t, 15);
        c.that(t.getAirport().getRunway("12R").isOpen(), "it reopens by itself after 10 minutes");
        t.fireEvent(Events.closeRunway("12L", 0));
        runTo(t, 100);
        c.that(!t.getAirport().getRunway("12L").isOpen(), "a closure with no duration stays closed");
        t.fireEvent(Events.reopenRunway("12L"));
        c.that(t.getAirport().getRunway("12L").isOpen(), "until it is reopened by hand");
        try {
            t.fireEvent(Events.reopenRunway("12L"));
            c.that(false, "reopening an open runway is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "reopening an open runway is rejected");
        }
        try {
            t.fireEvent(Events.closeRunway("99X", 5));
            c.that(false, "an unknown runway is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "an unknown runway is rejected");
        }
        t.fireEvent(Events.storm(5));
        try {
            t.fireEvent(Events.storm(5));
            c.that(false, "a second storm while all runways are closed is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "a second storm while all runways are closed is rejected");
        }
    }

    static void flightPlanChecks(Check c) throws Exception {
        ControlTower t = tower();
        t.addFlight(arr("EK1", "A320", 30, 60), dep("EK2", "A320", 120));
        try {
            t.addFlight(arr("EK1", "A320", 40, 60), null);
            c.that(false, "duplicate flight number is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "duplicate flight number is rejected");
        }
        try {
            t.addFlight(arr("EK5", "A320", 60, 60), dep("EK6", "A320", 50));
            c.that(false, "departure before its arrival is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "departure before its arrival is rejected");
        }
        try {
            t.addFlight(dep("EK7", "A320", 60), null);
            c.that(false, "a departure passed as an arrival is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "a departure passed as an arrival is rejected");
        }
        c.equal(2, t.flightCount(), "only the valid pair was added");
    }

    static void generator(Check c) throws Exception {
        TrafficGenerator g1 = new TrafficGenerator(7, 0.2);
        TrafficGenerator g2 = new TrafficGenerator(7, 0.2);
        List<String> s1 = new ArrayList<>();
        List<String> s2 = new ArrayList<>();
        for (int m = 0; m < 100; m++) {
            g1.generate(m).forEach(f -> s1.add(f.getFlightNumber() + f.getAircraft().getModel()));
            g2.generate(m).forEach(f -> s2.add(f.getFlightNumber() + f.getAircraft().getModel()));
        }
        c.that(!s1.isEmpty(), "generator creates extra flights");
        c.equal(s1, s2, "same seed gives exactly the same traffic");
        Set<String> unique = new HashSet<>(s1);
        c.equal(s1.size(), unique.size(), "generated flight numbers are unique");
        c.that(new TrafficGenerator(1, 0).generate(5).isEmpty(), "rate 0 creates nothing");

        ControlTower t = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(), new TrafficGenerator(3, 0.1));
        runTo(t, 400);
        c.that(t.flightCount() > 0, "generated traffic flows through the tower (" + t.flightCount() + " flights)");
        boolean earlyDone = t.flightSnapshots().stream()
                .filter(f -> f.isArrival() && f.scheduledTime() < 200)
                .allMatch(f -> f.status() == FlightStatus.LANDED || f.status() == FlightStatus.DIVERTED);
        c.that(earlyDone, "every generated flight from the first 200 minutes has finished");
    }

    static void statistics(Check c) throws Exception {
        ControlTower t = tower();
        t.addFlight(arr("EK1", "A380", 30, 80), dep("EK2", "A380", 30 + 90 + 15));
        t.addFlight(arr("EK3", "A380", 30, 80), dep("EK4", "A380", 30 + 90 + 15));
        t.addFlight(arr("EK5", "A380", 30, 80), dep("EK6", "A380", 30 + 90 + 15));
        runTo(t, 200);
        Statistics s = t.statistics();
        c.equal(3, s.getLandings(), "3 landings counted");
        c.equal(3, s.getDepartures(), "3 departures counted");
        c.equal(0, s.getDiversions(), "no diversions");
        c.that(s.getAvgArrivalDelay() > 0 && s.getAvgArrivalDelay() < 2, "three A380s at once give a small average delay ("
                + String.format("%.2f", s.getAvgArrivalDelay()) + ")");
        c.equal(100.0, s.getOnTimePercent(), "all within 15 min, so 100% on time");
        int total = s.getMovementsByHour().values().stream().mapToInt(Integer::intValue).sum();
        c.equal(6, total, "movements by hour add up to 6");
        c.that(s.getRunwayUtilisation() > 0, "runway utilisation is above zero");
    }
}
