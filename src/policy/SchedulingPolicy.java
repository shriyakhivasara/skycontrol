package policy;

import java.util.Comparator;
import model.Flight;

/**
 * Decides the order in which waiting flights get a runway.
 * Different policies can be swapped in while the simulation is running.
 */
public interface SchedulingPolicy {
    /** Orders flights: the one that should go first compares as smallest. */
    Comparator<Flight> order();

    /** Short code used by the API, e.g. "FCFS". */
    String code();

    /** Human-readable name for screens. */
    String displayName();
}
