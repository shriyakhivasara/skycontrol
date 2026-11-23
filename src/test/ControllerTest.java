package test;

import engine.ControlTower;
import engine.ControllerScore;
import exceptions.GateUnavailableException;
import exceptions.InvalidEventException;
import exceptions.InvalidFlightPlanException;
import exceptions.RunwayClosedException;
import exceptions.SeparationViolationException;
import model.Aircraft;
import model.Airport;
import model.Flight;
import model.FlightStatus;
import model.Gate;
import model.GateSize;
import model.Runway;
import policy.PriorityWithFuelPolicy;

/** Tests for manual control: clearances, the score, and the controller's actions. */
public class ControllerTest {

    public static void main(String[] args) throws Exception {
        Check c = new Check("ControllerTest");
        manualModeStopsAutomaticClearance(c);
        clearance(c);
        separationViolation(c);
        badClearances(c);
        sendToBack(c);
        divert(c);
        moveToGate(c);
        flightPlans(c);
        scoring(c);
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

    static void manualModeStopsAutomaticClearance(Check c) throws Exception {
        ControlTower t = tower();
        t.addFlight(arr("EK1", "A320", 30, 70), null);
        t.setManualMode(true);
        c.that(t.isManualMode(), "manual mode can be switched on");
        runTo(t, 35);
        c.equal(FlightStatus.HOLDING, t.getFlight("EK1").getStatus(), "in manual mode a waiting flight keeps holding");
        c.that(t.snapshot().manualMode(), "the snapshot reports manual mode");
        t.setManualMode(false);
        runTo(t, 36);
        c.equal(FlightStatus.AT_GATE, t.getFlight("EK1").getStatus(), "switching back to automatic lands it");
    }

    static void clearance(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("EK2", "A380", 30, 70);
        Flight d = Flight.departure("EK3", "Test Air", "LHR", Aircraft.of("A380"), 30 + 90 + 15);
        t.addFlight(a, d);
        t.setManualMode(true);
        runTo(t, 32);
        t.clearFlight("EK2", "12R");
        c.equal(FlightStatus.AT_GATE, a.getStatus(), "clearing a holding flight lands it");
        c.equal("12R", a.getRunwayName(), "on the runway the controller chose");
        c.equal(32, a.getActualTime(), "at the current minute");
        c.equal(2, a.delayMinutes(), "with the right delay");
        runTo(t, 135);
        c.equal(FlightStatus.READY_FOR_DEPARTURE, d.getStatus(), "its departure waits for clearance in manual mode");
        t.clearFlight("ek3", "12l");
        c.equal(FlightStatus.DEPARTED, d.getStatus(), "clearing the departure (lower-case names accepted) makes it take off");
    }

    static void separationViolation(Check c) throws Exception {
        ControlTower t = tower();
        t.addFlight(arr("EK4", "A380", 30, 70), null);
        t.addFlight(arr("PJ5", "G650", 30, 70), null);
        t.setManualMode(true);
        runTo(t, 30);
        t.clearFlight("EK4", "12L");
        int before = t.getScore().getPoints();
        try {
            t.clearFlight("PJ5", "12L");
            c.that(false, "a light jet straight behind an A380 is refused");
        } catch (SeparationViolationException e) {
            c.that(true, "a light jet straight behind an A380 is refused");
            c.equal(33, e.getSafeFrom(), "the exception says when it becomes safe (3 min later)");
            c.that(e.getMessage().contains("EK4"), "the message names the aircraft in front");
        }
        c.equal(before + ControllerScore.VIOLATION, t.getScore().getPoints(), "an unsafe attempt costs 25 points");
        c.equal(FlightStatus.HOLDING, t.getFlight("PJ5").getStatus(), "the refused flight is still holding");
        t.clearFlight("PJ5", "12R");
        c.equal(FlightStatus.AT_GATE, t.getFlight("PJ5").getStatus(), "the other runway is free, so that clearance works");
        boolean logged = t.snapshot().log().stream().anyMatch(e -> e.message().startsWith("UNSAFE"));
        c.that(logged, "the unsafe attempt is written to the log");
    }

    static void badClearances(Check c) throws Exception {
        ControlTower t = tower();
        t.addFlight(arr("EK6", "A320", 30, 70), null);
        t.addFlight(arr("EK8", "A320", 90, 70), null);
        t.setManualMode(true);
        runTo(t, 31);
        try {
            t.clearFlight("EK8", "12L");
            c.that(false, "a flight still on approach cannot be cleared");
        } catch (InvalidEventException e) {
            c.that(e.getMessage().contains("not waiting"), "a flight still on approach cannot be cleared");
        }
        try {
            t.clearFlight("ZZ999", "12L");
            c.that(false, "an unknown flight is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "an unknown flight is rejected");
        }
        try {
            t.clearFlight("EK6", "09X");
            c.that(false, "an unknown runway is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "an unknown runway is rejected");
        }
        t.closeRunway("12L", -1);
        try {
            t.clearFlight("EK6", "12L");
            c.that(false, "a closed runway is rejected");
        } catch (RunwayClosedException e) {
            c.that(true, "a closed runway is rejected");
        }
    }

    static void sendToBack(Check c) throws Exception {
        ControlTower t = tower();
        t.addFlight(arr("AA1", "A320", 30, 80), null);
        t.addFlight(arr("BB2", "A320", 31, 80), null);
        t.setManualMode(true);
        runTo(t, 32);
        c.equal("AA1", t.snapshot().queue().get(0).flightNumber(), "AA1 is first in the queue");
        t.sendToBackOfQueue("AA1");
        c.equal("BB2", t.snapshot().queue().get(0).flightNumber(), "after send-to-back, BB2 is first");
        c.equal("AA1", t.snapshot().queue().get(1).flightNumber(), "and AA1 is last");
        try {
            t.sendToBackOfQueue("CC3");
            c.that(false, "send-to-back of an unknown flight is rejected");
        } catch (InvalidEventException e) {
            c.that(true, "send-to-back of an unknown flight is rejected");
        }
    }

    static void divert(Check c) throws Exception {
        ControlTower t = tower();
        Flight a = arr("QR1", "A350", 30, 80);
        Flight d = Flight.departure("QR2", "Test Air", "DOH", Aircraft.of("A350"), 200);
        t.addFlight(a, d);
        t.setManualMode(true);
        try {
            t.divertByController("QR1");
            c.that(false, "a flight not yet in the air cannot divert");
        } catch (InvalidEventException e) {
            c.that(true, "a flight not yet in the air cannot divert");
        }
        runTo(t, 5);
        t.divertByController("QR1");
        c.equal(FlightStatus.DIVERTED, a.getStatus(), "the controller can divert an approaching flight");
        c.equal(FlightStatus.CANCELLED, d.getStatus(), "its departure is cancelled");
        c.equal(ControllerScore.DIVERSION, t.getScore().getPoints(), "a diversion in manual mode costs 50 points");
    }

    static void moveToGate(Check c) throws Exception {
        Airport small = new Airport("TST", "Test");
        small.addRunway(new Runway("09"));
        small.addGate(new Gate("B1", GateSize.NORMAL));
        small.addGate(new Gate("A1", GateSize.LARGE));
        ControlTower t = new ControlTower(small, new PriorityWithFuelPolicy(), null);
        t.addFlight(arr("EK10", "A380", 30, 80), null);
        t.addFlight(arr("EK12", "B777", 32, 80), null);
        t.addFlight(arr("FZ14", "B737", 34, 80), null);
        runTo(t, 36);
        c.equal("A1", t.getFlight("EK10").getGateId(), "first widebody takes the large gate A1");
        c.equal(FlightStatus.REMOTE_STAND, t.getFlight("EK12").getStatus(), "second widebody is on a remote stand");
        try {
            t.moveToGate("EK12", "B1");
            c.that(false, "a widebody cannot move to a normal gate");
        } catch (GateUnavailableException e) {
            c.that(e.getMessage().contains("too small"), "a widebody cannot move to a normal gate");
        }
        try {
            t.moveToGate("EK12", "A1");
            c.that(false, "it cannot move to a gate that is taken");
        } catch (GateUnavailableException e) {
            c.that(e.getMessage().contains("taken by EK10"), "it cannot move to a gate that is taken");
        }
        try {
            t.moveToGate("EK12", "Z9");
            c.that(false, "an unknown gate is rejected");
        } catch (GateUnavailableException e) {
            c.that(true, "an unknown gate is rejected");
        }
        t.moveToGate("FZ14", "B1");
        c.equal("B1", t.getFlight("FZ14").getGateId(), "moving an aircraft to the gate it is already at is allowed");
        runTo(t, 30 + 90);
        t.moveToGate("EK12", "A1");
        c.equal(FlightStatus.AT_GATE, t.getFlight("EK12").getStatus(), "once A1 is free, the remote-stand aircraft moves in");
        c.equal("A1", t.getFlight("EK12").getGateId(), "and its gate is A1");
        try {
            t.moveToGate("EK10", "B1");
            c.that(false, "an aircraft that has left cannot be moved");
        } catch (InvalidEventException e) {
            c.that(true, "an aircraft that has left cannot be moved");
        }
    }

    static void flightPlans(Check c) throws Exception {
        ControlTower t = tower();
        runTo(t, 60);
        try {
            t.fileFlightPlan(arr("XY1", "A320", 50, 70), null);
            c.that(false, "a flight plan arriving in the past is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(e.getMessage().contains("already passed"), "a flight plan arriving in the past is rejected");
        }
        Flight a = arr("XY2", "A320", 80, 70);
        Flight d = Flight.departure("XY3", "Test Air", "BOM", Aircraft.of("A320"), 150);
        t.fileFlightPlan(a, d);
        c.equal(2, t.flightCount(), "a valid flight plan and its return flight are added");
        try {
            t.fileFlightPlan(arr("XY2", "A320", 90, 70), null);
            c.that(false, "a duplicate flight number is rejected");
        } catch (InvalidFlightPlanException e) {
            c.that(true, "a duplicate flight number is rejected");
        }
        runTo(t, 51 + 30);
        c.that(a.getActualTime() > 0, "the filed flight appears on radar and lands");
    }

    static void scoring(Check c) throws Exception {
        ControllerScore s = new ControllerScore();
        s.movement(0, false);
        s.movement(30, false);
        s.movement(5, true);
        s.violation();
        s.diversion();
        int expected = ControllerScore.ON_TIME + ControllerScore.LATE + ControllerScore.ON_TIME
                + ControllerScore.EMERGENCY_BONUS + ControllerScore.VIOLATION + ControllerScore.DIVERSION;
        c.equal(expected, s.getPoints(), "score adds on-time, late, emergency bonus, violation and diversion");
        c.equal(3, s.getMovements(), "three movements counted");

        ControlTower t = tower();
        t.addFlight(arr("EK20", "A320", 30, 70), null);
        runTo(t, 35);
        c.equal(0, t.getScore().getPoints(), "automatic clearances do not change the controller's score");
    }
}
