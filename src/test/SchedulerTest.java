package test;

import engine.RunwayScheduler;
import engine.RunwayScheduler.Clearance;
import exceptions.InsufficientFuelException;
import exceptions.InvalidFlightPlanException;
import exceptions.RunwayClosedException;
import java.util.ArrayList;
import java.util.List;
import model.Aircraft;
import model.Flight;
import model.FlightStatus;
import model.GateSize;
import model.Priority;
import model.Runway;
import model.SeparationRules;
import model.SimTime;
import model.WakeCategory;
import policy.FcfsPolicy;
import policy.Policies;
import policy.PriorityWithFuelPolicy;

/** Tests for the model classes, separation rules, policies and the runway scheduler. */
public class SchedulerTest {

    public static void main(String[] args) throws Exception {
        Check c = new Check("SchedulerTest");
        aircraftTypes(c);
        flightValidation(c);
        fuel(c);
        separation(c);
        fcfsOrder(c);
        priorityOrder(c);
        emergencyJumpsQueue(c);
        twoRunways(c);
        closedRunway(c);
        policySwitch(c);
        simTime(c);
        System.exit(c.finish() == 0 ? 0 : 1);
    }

    static Flight arr(String no, String model, int time, double fuel) throws Exception {
        return Flight.arrival(no, "Test Air", "BOM", Aircraft.of(model), time, fuel);
    }

    static void aircraftTypes(Check c) throws Exception {
        c.equal(WakeCategory.HEAVY, Aircraft.of("A380").wakeCategory(), "A380 is a heavy aircraft");
        c.equal(GateSize.LARGE, Aircraft.of("a380").requiredGateSize(), "A380 needs a large gate (lower-case model accepted)");
        c.equal(WakeCategory.MEDIUM, Aircraft.of("B737").wakeCategory(), "B737 is medium");
        c.equal(GateSize.NORMAL, Aircraft.of("A320").requiredGateSize(), "A320 fits a normal gate");
        c.equal(120, Aircraft.of("B747F").turnaroundMinutes(), "cargo turnaround is 120 min");
        c.equal(WakeCategory.LIGHT, Aircraft.of("G650").wakeCategory(), "business jet is light");
        c.equal("Widebody", Aircraft.of("B777").category(), "B777 is a widebody");
        try {
            Aircraft.of("CONCORDE");
            c.that(false, "unknown aircraft model is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "unknown aircraft model is rejected");
        }
        c.that(GateSize.LARGE.fits(GateSize.NORMAL), "large gate fits a normal aircraft");
        c.that(!GateSize.NORMAL.fits(GateSize.LARGE), "normal gate does not fit a large aircraft");
    }

    static void flightValidation(Check c) throws Exception {
        String[] bad = {"", "E", "EMIRATES1", "ek512"};
        for (String no : bad) {
            try {
                arr(no, "A320", 10, 60);
                c.that(false, "flight number '" + no + "' is rejected");
            } catch (InvalidFlightPlanException e) {
                c.that(true, "flight number '" + no + "' is rejected");
            }
        }
        try {
            arr("EK512", "A380", 10, 5);
            c.that(false, "arrival with fuel below reserve is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "arrival with fuel below reserve is rejected");
        }
        try {
            arr("EK512", "A380", -5, 60);
            c.that(false, "flight scheduled before start is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "flight scheduled before start is rejected");
        }
        c.equal("EK512", arr("EK512", "A380", 10, 60).getFlightNumber(), "valid flight is created");
    }

    static void fuel(Check c) throws Exception {
        Flight f = arr("FZ101", "B737", 0, 22);
        f.setStatus(FlightStatus.HOLDING);
        c.that(!f.burnFuel(1), "burning to 21 min keeps NORMAL priority");
        c.that(f.burnFuel(2), "burning below 20 min raises priority (returns true)");
        c.equal(Priority.LOW_FUEL, f.getPriority(), "priority is now LOW_FUEL");
        c.that(!f.burnFuel(1), "already LOW_FUEL: no second change");
        try {
            f.burnFuel(20);
            c.that(false, "reaching the reserve throws InsufficientFuelException");
        } catch (InsufficientFuelException e) {
            c.that(true, "reaching the reserve throws InsufficientFuelException");
        }
        Flight onGround = arr("FZ102", "B737", 0, 30);
        onGround.setStatus(FlightStatus.AT_GATE);
        onGround.burnFuel(5);
        c.equal(30.0, onGround.getFuelMinutes(), "aircraft at the gate does not burn fuel");
    }

    static void separation(Check c) throws Exception {
        c.equal(3, SeparationRules.gapMinutes(WakeCategory.HEAVY, WakeCategory.LIGHT), "heavy then light needs 3 min");
        c.equal(1, SeparationRules.gapMinutes(WakeCategory.MEDIUM, WakeCategory.HEAVY), "medium then heavy needs 1 min");

        Runway r = new Runway("12L");
        Flight heavy = arr("EK1", "A380", 0, 60);
        Flight light = arr("JT1", "G650", 0, 60);
        Flight medium = arr("FZ1", "A320", 0, 60);
        c.that(r.isAvailableFor(heavy.getAircraft(), 0), "unused runway is available at once");
        r.clear(heavy, 10);
        c.equal(13, r.earliestTimeFor(light.getAircraft()), "light jet behind A380 must wait until minute 13");
        c.equal(12, r.earliestTimeFor(medium.getAircraft()), "A320 behind A380 may go at minute 12");
        c.that(!r.isAvailableFor(light.getAircraft(), 12), "light jet is not allowed at minute 12");
    }

    static RunwayScheduler oneRunway(policy.SchedulingPolicy p) {
        List<Runway> rs = new ArrayList<>();
        rs.add(new Runway("12L"));
        return new RunwayScheduler(rs, p);
    }

    static List<String> runUntilEmpty(RunwayScheduler s, int from) {
        List<String> order = new ArrayList<>();
        for (int t = from; t < from + 200 && s.queueSize() > 0; t++) {
            for (Clearance cl : s.assignSlots(t)) {
                order.add(cl.flight.getFlightNumber());
            }
        }
        return order;
    }

    static void fcfsOrder(Check c) throws Exception {
        RunwayScheduler s = oneRunway(new FcfsPolicy());
        Flight a = arr("AA1", "A320", 0, 60);
        Flight b = arr("BB2", "A320", 0, 15);   // low fuel, but FCFS ignores that
        b.setPriority(Priority.LOW_FUEL);
        s.requestLanding(a, 0);
        s.requestLanding(b, 1);
        c.equal(FlightStatus.HOLDING, a.getStatus(), "requesting a landing puts the flight in HOLDING");
        c.equal(List.of("AA1", "BB2"), runUntilEmpty(s, 1), "FCFS lands in request order even if the second is low on fuel");
        c.equal("12L", a.getRunwayName(), "cleared flight is told its runway");
        c.equal(1, a.getActualTime(), "first flight lands at minute 1");
    }

    static void priorityOrder(Check c) throws Exception {
        RunwayScheduler s = oneRunway(new PriorityWithFuelPolicy());
        Flight a = arr("AA1", "A320", 0, 60);
        Flight b = arr("BB2", "A320", 0, 17);
        Flight d = arr("DD4", "A320", 0, 12);
        b.setPriority(Priority.LOW_FUEL);
        d.setPriority(Priority.LOW_FUEL);
        s.requestLanding(a, 0);
        s.requestLanding(b, 1);
        s.requestLanding(d, 2);
        List<Flight> q = s.queueSnapshot();
        c.equal("DD4", q.get(0).getFlightNumber(), "lowest-fuel flight is at the front");
        c.equal("AA1", q.get(2).getFlightNumber(), "normal flight is at the back");
        c.equal(List.of("DD4", "BB2", "AA1"), runUntilEmpty(s, 2), "priority policy lands low-fuel flights first");
    }

    static void emergencyJumpsQueue(Check c) throws Exception {
        RunwayScheduler s = oneRunway(new PriorityWithFuelPolicy());
        Flight[] fs = new Flight[4];
        for (int i = 0; i < 4; i++) {
            fs[i] = arr("QR" + (100 + i), "A320", 0, 60);
            s.requestLanding(fs[i], i);
        }
        fs[3].declareEmergency();
        s.reprioritise(fs[3]);
        c.equal("QR103", s.queueSnapshot().get(0).getFlightNumber(), "MAYDAY flight moves to the front after reprioritise");
        c.equal("QR103", runUntilEmpty(s, 4).get(0), "MAYDAY flight lands first");
    }

    static void twoRunways(Check c) throws Exception {
        List<Runway> rs = List.of(new Runway("12L"), new Runway("12R"));
        RunwayScheduler s = new RunwayScheduler(rs, new PriorityWithFuelPolicy());
        for (int i = 0; i < 4; i++) {
            s.requestLanding(arr("EK" + (10 + i), "A380", 0, 60), 0);
        }
        List<Clearance> first = s.assignSlots(0);
        c.equal(2, first.size(), "two runways clear two flights in the same minute");
        c.that(!first.get(0).runway.getName().equals(first.get(1).runway.getName()), "they use different runways");
        c.equal(0, s.assignSlots(1).size(), "heavy-behind-heavy gap blocks both runways at minute 1");
        c.equal(2, s.assignSlots(2).size(), "both runways free again at minute 2");
    }

    static void closedRunway(Check c) throws Exception {
        Runway r12l = new Runway("12L");
        Runway r12r = new Runway("12R");
        RunwayScheduler s = new RunwayScheduler(List.of(r12l, r12r), new PriorityWithFuelPolicy());
        r12r.setOpen(false);
        s.requestLanding(arr("EK20", "A320", 0, 60), 0);
        s.requestLanding(arr("EK21", "A320", 0, 60), 0);
        List<Clearance> cl = s.assignSlots(0);
        c.equal(1, cl.size(), "only one flight cleared when one runway is closed");
        c.equal("12L", cl.get(0).runway.getName(), "and it uses the open runway");
        try {
            r12r.clear(arr("EK22", "A320", 0, 60), 0);
            c.that(false, "clearing onto a closed runway throws RunwayClosedException");
        } catch (RunwayClosedException e) {
            c.that(true, "clearing onto a closed runway throws RunwayClosedException");
        }
        r12l.setOpen(false);
        c.equal(0, s.assignSlots(5).size(), "nothing is cleared when every runway is closed");
        c.equal(1, s.queueSize(), "the waiting flight stays in the queue");
    }

    static void policySwitch(Check c) throws Exception {
        RunwayScheduler s = oneRunway(new FcfsPolicy());
        Flight a = arr("AA1", "A320", 0, 60);
        Flight b = arr("BB2", "A320", 0, 60);
        s.requestLanding(a, 0);
        s.requestLanding(b, 1);
        b.declareEmergency();
        c.equal("AA1", s.queueSnapshot().get(0).getFlightNumber(), "under FCFS the emergency does not jump the queue");
        s.setPolicy(new PriorityWithFuelPolicy());
        c.equal("BB2", s.queueSnapshot().get(0).getFlightNumber(), "after switching policy the emergency is first");
        c.equal("PRIORITY_FUEL", s.getPolicy().code(), "policy code is updated");
        c.that(Policies.byCode("fcfs") instanceof FcfsPolicy, "policy lookup by code works");
        c.that(Policies.byCode("nonsense") == null, "unknown policy code returns null");
        c.that(s.remove(a), "a flight can be removed from the queue (diversion)");
        c.equal(1, s.queueSize(), "queue shrinks after removal");
    }

    static void simTime(Check c) {
        c.equal("06:00", SimTime.format(0), "minute 0 is 06:00");
        c.equal("07:15", SimTime.format(75), "minute 75 is 07:15");
        c.equal(75, SimTime.parse("07:15"), "07:15 parses back to minute 75");
    }
}
