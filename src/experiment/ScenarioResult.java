package experiment;

/** The outcome of one simulated day under one policy. */
public record ScenarioResult(String policy, long seed, int landings, int departures, int diversions,
                             double avgArrivalDelay, double avgDepartureDelay, int maxArrivalDelay,
                             double onTimePercent) {
}
