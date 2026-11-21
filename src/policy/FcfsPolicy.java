package policy;

import java.util.Comparator;
import model.Flight;

/**
 * First come, first served: flights use the runway strictly in the order they asked.
 * Ignores fuel and emergencies. This is the simple baseline the experiment compares against.
 */
public class FcfsPolicy implements SchedulingPolicy {
    @Override
    public Comparator<Flight> order() {
        return Comparator.comparingInt(Flight::getRequestTime)
                .thenComparingLong(Flight::getSequence);
    }

    @Override public String code() { return "FCFS"; }
    @Override public String displayName() { return "First come, first served"; }
}
