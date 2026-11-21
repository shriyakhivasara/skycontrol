package policy;

import java.util.Comparator;
import model.Flight;
import model.Priority;

/**
 * Emergencies first, then low-fuel flights (lowest fuel first), then everyone else
 * in the order they asked.
 */
public class PriorityWithFuelPolicy implements SchedulingPolicy {
    @Override
    public Comparator<Flight> order() {
        return Comparator.comparingInt((Flight f) -> -f.getPriority().rank())
                .thenComparingDouble(f -> f.getPriority() == Priority.LOW_FUEL ? f.getFuelMinutes() : 0)
                .thenComparingInt(Flight::getRequestTime)
                .thenComparingLong(Flight::getSequence);
    }

    @Override public String code() { return "PRIORITY_FUEL"; }
    @Override public String displayName() { return "Priority (emergency, then fuel)"; }
}
