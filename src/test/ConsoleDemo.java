package test;

import engine.RunwayScheduler;
import engine.RunwayScheduler.Clearance;
import java.util.List;
import model.Aircraft;
import model.Airport;
import model.Flight;
import model.SimTime;
import policy.PriorityWithFuelPolicy;

/** Step-by-step console walkthrough of the runway scheduler, no screens needed. */
public class ConsoleDemo {
    public static void main(String[] args) throws Exception {
        Airport dxb = Airport.createDefault();
        RunwayScheduler scheduler = new RunwayScheduler(dxb.getRunways(), new PriorityWithFuelPolicy());

        Object[][] traffic = {
                {"EK512", "Emirates", "BOM", "A380", 50.0},
                {"FZ201", "flydubai", "KHI", "B737", 45.0},
                {"QR1018", "Qatar Airways", "DOH", "A350", 60.0},
                {"6E1451", "IndiGo", "DEL", "A320", 40.0},
                {"EK9881", "Emirates SkyCargo", "HKG", "B747F", 55.0},
                {"JT77", "Private", "LTN", "G650", 70.0},
                {"AI933", "Air India", "COK", "A320", 18.0},
                {"FZ333", "flydubai", "CAI", "B738", 50.0},
        };
        System.out.println("Eight flights arrive at minute 0 and ask to land at " + dxb.getName() + "\n");
        Flight emergency = null;
        for (Object[] t : traffic) {
            Flight f = Flight.arrival((String) t[0], (String) t[1], (String) t[2],
                    Aircraft.of((String) t[3]), 0, (Double) t[4]);
            if (f.getFuelMinutes() < Flight.LOW_FUEL_THRESHOLD) {
                f.setPriority(model.Priority.LOW_FUEL);
            }
            if (f.getFlightNumber().equals("FZ333")) {
                emergency = f;
            }
            scheduler.requestLanding(f, 0);
        }
        System.out.println("FZ333 declares MAYDAY (engine failure).\n");
        emergency.declareEmergency();
        scheduler.reprioritise(emergency);

        System.out.println("Queue order (" + scheduler.getPolicy().displayName() + "):");
        int i = 1;
        for (Flight f : scheduler.queueSnapshot()) {
            System.out.printf("  %d. %-7s %-6s %-12s %-9s fuel %2.0f min%n", i++, f.getFlightNumber(),
                    f.getAircraft().getModel(), f.getAircraft().category(), f.getPriority(), f.getFuelMinutes());
        }
        System.out.println("\nLandings:");
        for (int t = 0; scheduler.queueSize() > 0; t++) {
            List<Clearance> cleared = scheduler.assignSlots(t);
            for (Clearance c : cleared) {
                System.out.printf("  %s  %-7s lands on %s  (%s)%n", SimTime.format(t), c.flight.getFlightNumber(),
                        c.runway.getName(), c.flight.getAircraft().category());
            }
        }
        System.out.println("\nNotice: the light G650 waits longer when it follows a heavy jet (wake turbulence gap).");
    }
}
