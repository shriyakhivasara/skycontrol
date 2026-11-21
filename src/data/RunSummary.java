package data;

/** One past simulation run as stored in the database. */
public record RunSummary(int id, String startedAt, String policy, int landings, int departures,
                         int diversions, double avgArrivalDelay, double onTimePercent) {
}
