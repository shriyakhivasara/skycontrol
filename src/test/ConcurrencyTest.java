package test;

import data.ScheduleLoader;
import data.ScheduledFlight;
import engine.ControlTower;
import engine.SimClock;
import engine.TowerSnapshot;
import engine.TrafficGenerator;
import events.Events;
import exceptions.SkyControlException;
import experiment.PolicyExperiment;
import experiment.ScenarioResult;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import model.Airport;
import model.FlightSnapshot;
import model.FlightStatus;
import policy.FcfsPolicy;
import policy.PriorityWithFuelPolicy;

/**
 * Stress test for thread safety: the clock thread runs the simulation at full speed while
 * several other threads fire events, switch policy and read snapshots at the same time.
 * Afterwards no flight may have landed or taken off twice and no gate may be double-booked.
 */
public class ConcurrencyTest {

    public static void main(String[] args) throws Exception {
        Check c = new Check("ConcurrencyTest");
        stress(c);
        clockThread(c);
        experimentRepeatable(c);
        System.exit(c.finish() == 0 ? 0 : 1);
    }

    static ControlTower loadedTower(long seed) throws Exception {
        ControlTower t = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(),
                new TrafficGenerator(seed, 0.08));
        for (ScheduledFlight f : new ScheduleLoader().load(Paths.get("data", "schedule.csv"))) {
            t.addFlight(f.arrival(), f.departure());
        }
        return t;
    }

    static void stress(Check c) throws Exception {
        ControlTower t = loadedTower(9);
        Map<String, AtomicInteger> landings = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> takeoffs = new ConcurrentHashMap<>();
        t.addListener((f, msg, minute) -> {
            if (f == null) {
                return;
            }
            if (msg.startsWith("landed")) {
                landings.computeIfAbsent(f.getFlightNumber(), k -> new AtomicInteger()).incrementAndGet();
            } else if (msg.startsWith("took off")) {
                takeoffs.computeIfAbsent(f.getFlightNumber(), k -> new AtomicInteger()).incrementAndGet();
            }
        });

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger unexpected = new AtomicInteger();
        AtomicInteger badSnapshots = new AtomicInteger();
        AtomicInteger eventsApplied = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(5);

        Thread clock = new Thread(() -> {
            try {
                for (int i = 0; i < 700; i++) {
                    t.tick();
                    Thread.sleep(1);   // give the other threads time to act between minutes
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                unexpected.incrementAndGet();
                e.printStackTrace();
            } finally {
                running.set(false);
                done.countDown();
            }
        }, "test-clock");

        Runnable eventStorm = () -> {
            java.util.Random rnd = new java.util.Random(Thread.currentThread().getName().hashCode());
            try {
                while (running.get()) {
                    try {
                        switch (rnd.nextInt(5)) {
                            case 0: t.fireEvent(Events.closeRunway(rnd.nextBoolean() ? "12L" : "12R", 1 + rnd.nextInt(5))); break;
                            case 1: t.fireEvent(Events.reopenRunway(rnd.nextBoolean() ? "12L" : "12R")); break;
                            case 2: {
                                for (FlightSnapshot f : t.flightSnapshots()) {
                                    if (f.status().isAirborne() && rnd.nextInt(10) == 0) {
                                        t.fireEvent(Events.emergency(f.flightNumber()));
                                        break;
                                    }
                                }
                                break;
                            }
                            case 3: t.setPolicy(rnd.nextBoolean() ? new FcfsPolicy() : new PriorityWithFuelPolicy()); break;
                            default: t.fireEvent(Events.storm(1 + rnd.nextInt(3))); break;
                        }
                        eventsApplied.incrementAndGet();
                    } catch (SkyControlException expected) {
                        // invalid events are rejected cleanly; that is fine
                    }
                    Thread.yield();
                }
            } catch (RuntimeException e) {
                unexpected.incrementAndGet();
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        };

        Runnable reader = () -> {
            try {
                while (running.get()) {
                    TowerSnapshot s = t.snapshot();
                    Set<String> seen = new HashSet<>();
                    for (TowerSnapshot.GateStatus g : s.gates()) {
                        if (g.occupant() != null && !seen.add(g.occupant())) {
                            badSnapshots.incrementAndGet();   // same aircraft at two gates
                        }
                    }
                    for (FlightSnapshot q : s.queue()) {
                        if (q.status() != FlightStatus.HOLDING && q.status() != FlightStatus.READY_FOR_DEPARTURE) {
                            badSnapshots.incrementAndGet();   // something in the queue that should not be
                        }
                    }
                }
            } catch (RuntimeException e) {
                unexpected.incrementAndGet();
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        };

        Thread[] others = {
                new Thread(eventStorm, "events-1"), new Thread(eventStorm, "events-2"),
                new Thread(reader, "reader-1"), new Thread(reader, "reader-2")};
        clock.start();
        for (Thread th : others) {
            th.start();
        }
        done.await();

        c.equal(0, unexpected.get(), "no unexpected exceptions in any of the 5 threads");
        c.equal(0, badSnapshots.get(), "every snapshot was consistent (no double-booked gates, clean queue)");
        c.that(eventsApplied.get() > 50, "many events were applied while the clock ran (" + eventsApplied.get() + ")");
        boolean onceEach = landings.values().stream().allMatch(n -> n.get() == 1)
                && takeoffs.values().stream().allMatch(n -> n.get() == 1);
        c.that(onceEach, "no flight landed or took off twice (" + landings.size() + " landings, "
                + takeoffs.size() + " take-offs)");

        Map<String, FlightSnapshot> byNumber = new HashMap<>();
        for (FlightSnapshot f : t.flightSnapshots()) {
            byNumber.put(f.flightNumber(), f);
        }
        boolean diversionsNeverLanded = byNumber.values().stream()
                .filter(f -> f.status() == FlightStatus.DIVERTED)
                .noneMatch(f -> landings.containsKey(f.flightNumber()));
        c.that(diversionsNeverLanded, "no diverted flight is also recorded as landed");
        boolean takeoffsHadArrival = takeoffs.keySet().stream().allMatch(byNumber::containsKey);
        c.that(takeoffsHadArrival, "every take-off belongs to a known flight");
    }

    static void clockThread(Check c) throws Exception {
        ControlTower t = loadedTower(4);
        SimClock clock = new SimClock(t);
        clock.setSpeed(30);
        clock.start();
        Thread.sleep(400);
        int m1 = t.getMinute();
        c.that(m1 > 3, "the clock thread advances the simulation on its own (minute " + m1 + ")");
        clock.pause();
        Thread.sleep(150);
        int m2 = t.getMinute();
        Thread.sleep(300);
        c.equal(m2, t.getMinute(), "pause stops the clock");
        clock.resume();
        Thread.sleep(300);
        c.that(t.getMinute() > m2, "resume starts it again");
        clock.stop();
        Thread.sleep(150);
        int m3 = t.getMinute();
        Thread.sleep(300);
        c.equal(m3, t.getMinute(), "stop ends the clock thread");
    }

    static void experimentRepeatable(Check c) throws Exception {
        List<ScenarioResult> a = PolicyExperiment.runAll(Paths.get("data", "schedule.csv"));
        List<ScenarioResult> b = PolicyExperiment.runAll(Paths.get("data", "schedule.csv"));
        c.equal(a, b, "the experiment gives identical results when run twice in parallel");
        double fcfs = a.stream().filter(r -> r.policy().equals("FCFS")).mapToInt(ScenarioResult::diversions).sum();
        double prio = a.stream().filter(r -> r.policy().equals("PRIORITY_FUEL")).mapToInt(ScenarioResult::diversions).sum();
        c.that(prio < fcfs, "across all seeds the priority policy diverts fewer flights (" + (int) prio + " vs " + (int) fcfs + ")");
    }
}
