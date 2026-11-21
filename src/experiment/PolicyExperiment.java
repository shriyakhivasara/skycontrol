package experiment;

import data.ScheduleLoader;
import data.ScheduledFlight;
import engine.ControlTower;
import engine.TrafficGenerator;
import events.Events;
import exceptions.SkyControlException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import model.Airport;
import model.FlightSnapshot;
import model.FlightStatus;
import model.SimTime;
import policy.FcfsPolicy;
import policy.PriorityWithFuelPolicy;
import policy.SchedulingPolicy;
import report.Statistics;

/**
 * The experiment for the report: does the priority policy actually help?
 *
 * Each scenario is the same busy day: the full schedule, extra random traffic (seeded),
 * a 25-minute thunderstorm at 07:30, runway 12R closed for 30 minutes at 08:40 and a
 * MAYDAY at 08:00. Every scenario is run once with FCFS and once with the priority
 * policy, using 5 different random seeds. The runs are independent, so they run in
 * parallel on a thread pool.
 *
 * Usage: java experiment.PolicyExperiment [schedule.csv]
 */
public class PolicyExperiment {
    public static final long[] SEEDS = {1, 2, 3, 4, 5};
    private static final int STORM_AT = SimTime.parse("07:30");
    private static final int MAYDAY_AT = SimTime.parse("08:00");
    private static final int CLOSURE_AT = SimTime.parse("08:40");
    private static final int END = 12 * 60;     // stop at 18:00 at the latest

    public static void main(String[] args) throws Exception {
        Path schedule = Paths.get(args.length > 0 ? args[0] : "data/schedule.csv");
        List<ScenarioResult> results = runAll(schedule);
        print(results);
        Path out = Paths.get("data", "exports", "experiment_results.csv");
        export(results, out);
        System.out.println("\nResults saved to " + out);
    }

    /** Runs every seed with both policies on a thread pool and returns the results in order. */
    public static List<ScenarioResult> runAll(Path schedule) throws Exception {
        int threads = Math.max(2, Math.min(SEEDS.length * 2, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ScenarioResult>> futures = new ArrayList<>();
            for (long seed : SEEDS) {
                futures.add(pool.submit(() -> runScenario(schedule, new FcfsPolicy(), seed)));
                futures.add(pool.submit(() -> runScenario(schedule, new PriorityWithFuelPolicy(), seed)));
            }
            List<ScenarioResult> results = new ArrayList<>();
            for (Future<ScenarioResult> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    /** Simulates one full day with one policy and one seed. Each call builds its own airport. */
    public static ScenarioResult runScenario(Path schedule, SchedulingPolicy policy, long seed) throws Exception {
        ControlTower tower = new ControlTower(Airport.createDefault(), policy, new TrafficGenerator(seed, 0.03));
        for (ScheduledFlight f : new ScheduleLoader().load(schedule)) {
            tower.addFlight(f.arrival(), f.departure());
        }
        boolean maydayDone = false;
        while (tower.getMinute() < END) {
            tower.tick();
            int now = tower.getMinute();
            try {
                if (now == STORM_AT) {
                    tower.fireEvent(Events.storm(25));
                }
                if (now == CLOSURE_AT) {
                    tower.fireEvent(Events.closeRunway("12R", 30));
                }
                if (now >= MAYDAY_AT && !maydayDone) {
                    // the first aircraft on approach at 08:00 declares MAYDAY (same flight in both policies)
                    for (FlightSnapshot f : tower.flightSnapshots()) {
                        if (f.status() == FlightStatus.APPROACHING) {
                            tower.fireEvent(Events.emergency(f.flightNumber()));
                            maydayDone = true;
                            break;
                        }
                    }
                }
            } catch (SkyControlException e) {
                // an event that cannot apply (e.g. runway already closed) is simply skipped
            }
            if (now > CLOSURE_AT + 30 && tower.allFinished()) {
                break;
            }
        }
        Statistics s = tower.statistics();
        return new ScenarioResult(policy.code(), seed, s.getLandings(), s.getDepartures(), s.getDiversions(),
                s.getAvgArrivalDelay(), s.getAvgDepartureDelay(), s.getMaxArrivalDelay(), s.getOnTimePercent());
    }

    private static void print(List<ScenarioResult> results) {
        System.out.println("SkyControl policy experiment: same traffic and events, two policies, "
                + SEEDS.length + " seeds\n");
        System.out.printf("%-14s %4s %8s %10s %10s %13s %13s %11s %8s%n", "Policy", "Seed", "Landings",
                "Take-offs", "Diversions", "Avg arr delay", "Avg dep delay", "Max arr dly", "On time");
        for (ScenarioResult r : results) {
            System.out.printf("%-14s %4d %8d %10d %10d %11.1f m %11.1f m %9d m %7.0f%%%n", r.policy(), r.seed(),
                    r.landings(), r.departures(), r.diversions(), r.avgArrivalDelay(), r.avgDepartureDelay(),
                    r.maxArrivalDelay(), r.onTimePercent());
        }
        System.out.println();
        System.out.println("Averages over all seeds:");
        for (String p : new String[]{"FCFS", "PRIORITY_FUEL"}) {
            double div = 0, arr = 0, dep = 0, ot = 0;
            int n = 0;
            for (ScenarioResult r : results) {
                if (r.policy().equals(p)) {
                    div += r.diversions();
                    arr += r.avgArrivalDelay();
                    dep += r.avgDepartureDelay();
                    ot += r.onTimePercent();
                    n++;
                }
            }
            System.out.printf("  %-14s diversions %.1f   avg arrival delay %.1f min   avg departure delay %.1f min   on time %.0f%%%n",
                    p, div / n, arr / n, dep / n, ot / n);
        }
    }

    private static void export(List<ScenarioResult> results, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
            out.println("policy,seed,landings,departures,diversions,avg_arrival_delay,avg_departure_delay,max_arrival_delay,on_time_pct");
            for (ScenarioResult r : results) {
                out.printf("%s,%d,%d,%d,%d,%.2f,%.2f,%d,%.1f%n", r.policy(), r.seed(), r.landings(), r.departures(),
                        r.diversions(), r.avgArrivalDelay(), r.avgDepartureDelay(), r.maxArrivalDelay(), r.onTimePercent());
            }
        }
    }
}
