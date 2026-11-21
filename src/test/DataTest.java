package test;

import data.AsyncStorageWriter;
import data.InMemoryStorage;
import data.RunSummary;
import data.ScheduleLoader;
import data.ScheduledFlight;
import data.SqliteStorage;
import data.Storage;
import engine.ControlTower;
import exceptions.InvalidFlightPlanException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import model.Airport;
import model.FlightSnapshot;
import model.FlightStatus;
import policy.PriorityWithFuelPolicy;
import report.ReportExporter;

/** Tests for the schedule loader, both storage types, the background writer and CSV export. */
public class DataTest {

    public static void main(String[] args) throws Exception {
        Check c = new Check("DataTest");
        Path tmp = Files.createTempDirectory("skycontrol-test");
        loaderGood(c);
        loaderBad(c);
        realSchedule(c);
        storage(c, new InMemoryStorage(), "InMemoryStorage");
        if (SqliteStorage.driverAvailable()) {
            storage(c, new SqliteStorage(tmp.resolve("test.db").toString()), "SqliteStorage");
            sqliteSurvivesRestart(c, tmp.resolve("restart.db").toString());
        } else {
            c.that(false, "SQLite driver is on the classpath (run with lib/*)");
        }
        asyncWriter(c);
        export(c, tmp);
        System.exit(c.finish() == 0 ? 0 : 1);
    }

    static List<ScheduledFlight> parse(String text) throws Exception {
        return new ScheduleLoader().parse(new BufferedReader(new StringReader(text)));
    }

    static void loaderGood(Check c) throws Exception {
        String csv = "# comment line\n"
                + "arrival_flight,airline,origin,aircraft,arrival_time,fuel_min,departure_flight,destination,departure_time\n"
                + "\n"
                + "EK512,Emirates,BOM,A380,07:15,60,EK513,BOM,09:10\n"
                + "PJ1,Private,LTN,G650,08:00,70,,,\n";
        List<ScheduledFlight> fs = parse(csv);
        c.equal(2, fs.size(), "comments, header and blank lines are skipped");
        c.equal("EK512", fs.get(0).arrival().getFlightNumber(), "arrival flight number read");
        c.equal(75, fs.get(0).arrival().getScheduledTime(), "07:15 becomes minute 75");
        c.equal("Widebody", fs.get(0).arrival().getAircraft().category(), "aircraft type built from model");
        c.equal(60.0, fs.get(0).arrival().getFuelMinutes(), "fuel read");
        c.equal("EK513", fs.get(0).departure().getFlightNumber(), "departure read");
        c.equal(190, fs.get(0).departure().getScheduledTime(), "09:10 becomes minute 190");
        c.that(fs.get(1).departure() == null, "empty departure columns mean no departure");
    }

    static void loaderBad(Check c) throws Exception {
        String[][] cases = {
                {"EK1,Emirates,BOM,A380,07:15,60,EK2,BOM", "line 1", "wrong column count names the line"},
                {"EK1,Emirates,BOM,CONCORDE,07:15,60,EK2,BOM,09:00", "unknown aircraft", "unknown aircraft is reported"},
                {"EK1,Emirates,BOM,A380,7h15,60,EK2,BOM,09:00", "bad number or time", "bad time is reported"},
                {"EK1,Emirates,BOM,A380,07:15,lots,EK2,BOM,09:00", "bad number or time", "bad fuel is reported"},
                {"EK1,Emirates,BOM,A380,09:15,60,EK2,BOM,09:00", "leaves before", "departure before arrival is reported"},
                {"EK1,Emirates,BOM,A380,07:15,3,EK2,BOM,09:00", "without enough fuel", "too little fuel is reported"},
        };
        for (String[] k : cases) {
            try {
                parse(k[0]);
                c.that(false, k[2]);
            } catch (InvalidFlightPlanException e) {
                c.that(e.getMessage().contains(k[1]) && e.getMessage().contains("line 1"),
                        k[2] + " (" + e.getMessage() + ")");
            }
        }
    }

    static void realSchedule(Check c) throws Exception {
        Path p = Paths.get("data", "schedule.csv");
        c.that(Files.exists(p), "data/schedule.csv exists");
        List<ScheduledFlight> fs = new ScheduleLoader().load(p);
        c.that(fs.size() >= 50, "schedule has at least 50 flights (" + fs.size() + ")");
        ControlTower t = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(), null);
        for (ScheduledFlight f : fs) {
            t.addFlight(f.arrival(), f.departure());
        }
        c.equal(fs.size() * 2, t.flightCount(), "every arrival and departure loads into the tower without duplicates");
        while (t.getMinute() < 600 && !t.allFinished()) {
            t.tick();
        }
        c.that(t.allFinished(), "the whole schedule runs to completion by " + model.SimTime.format(t.getMinute()));
    }

    static void storage(Check c, Storage s, String name) throws Exception {
        s.open();
        int run = s.startRun("PRIORITY_FUEL");
        c.that(run > 0, name + ": a run gets an id");
        FlightSnapshot f = sample(FlightStatus.HOLDING, -1);
        s.saveFlight(run, f);
        s.saveFlight(run, sample(FlightStatus.AT_GATE, 80));   // same flight, newer state
        List<FlightSnapshot> back = s.loadFlights(run);
        c.equal(1, back.size(), name + ": saving the same flight twice keeps one row");
        c.equal(FlightStatus.AT_GATE, back.get(0).status(), name + ": the latest state is kept");
        c.equal(80, back.get(0).actualTime(), name + ": actual time stored");
        c.equal("Emirates", back.get(0).airline(), name + ": airline stored");
        s.saveEvent(run, new engine.LogEntry(75, "EK512", "landed"));
        s.saveEvent(run, new engine.LogEntry(76, null, "Runway 12R CLOSED"));
        c.equal(2, s.countEvents(run), name + ": events stored, including airport-wide ones");
        ControlTower t = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(), null);
        s.finishRun(run, t.statistics());
        List<RunSummary> runs = s.listRuns();
        c.that(runs.stream().anyMatch(r -> r.id() == run && r.policy().equals("PRIORITY_FUEL")),
                name + ": the run is listed with its policy");
        s.close();
    }

    static void sqliteSurvivesRestart(Check c, String path) throws Exception {
        SqliteStorage first = new SqliteStorage(path);
        first.open();
        int run = first.startRun("FCFS");
        first.saveFlight(run, sample(FlightStatus.DEPARTED, 90));
        first.close();
        SqliteStorage second = new SqliteStorage(path);
        second.open();
        c.equal(1, second.loadFlights(run).size(), "SQLite: data is still there after closing and reopening");
        second.close();
    }

    static FlightSnapshot sample(FlightStatus status, int actual) {
        return new FlightSnapshot("EK512", "Emirates", model.FlightType.ARRIVAL, "BOM", "A380", "Widebody",
                status, model.Priority.NORMAL, 30, 75, actual, actual < 0 ? -1 : actual - 75, "12L", "A1");
    }

    static void asyncWriter(Check c) throws Exception {
        InMemoryStorage s = new InMemoryStorage();
        int run = s.startRun("PRIORITY_FUEL");
        ControlTower t = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(), null);
        AsyncStorageWriter w = new AsyncStorageWriter(s, run);
        t.addListener(w);
        for (ScheduledFlight f : new ScheduleLoader().load(Paths.get("data", "schedule.csv"))) {
            t.addFlight(f.arrival(), f.departure());
        }
        while (t.getMinute() < 600 && !t.allFinished()) {
            t.tick();
        }
        w.stop();
        c.that(w.getLastError() == null, "background writer reports no errors");
        c.equal(0, w.getPending(), "background writer emptied its queue");
        c.equal(t.flightCount(), s.loadFlights(run).size(), "every flight was saved by the background thread");
        boolean allFinal = s.loadFlights(run).stream()
                .allMatch(f -> f.status().isFinished() || f.status() == FlightStatus.LANDED);
        c.that(allFinal, "the saved state of each flight is its final state");
        c.that(s.countEvents(run) > t.flightCount(), "the log was saved too (" + s.countEvents(run) + " events)");
    }

    static void export(Check c, Path dir) throws Exception {
        ControlTower t = new ControlTower(Airport.createDefault(), new PriorityWithFuelPolicy(), null);
        for (ScheduledFlight f : new ScheduleLoader().load(Paths.get("data", "schedule.csv"))) {
            t.addFlight(f.arrival(), f.departure());
        }
        while (t.getMinute() < 600 && !t.allFinished()) {
            t.tick();
        }
        ReportExporter ex = new ReportExporter();
        Path flights = ex.exportFlights(t.flightSnapshots(), dir.resolve("flights.csv"));
        List<String> lines = Files.readAllLines(flights);
        c.equal(t.flightCount() + 1, lines.size(), "flights export has a header plus one row per flight");
        c.that(lines.get(0).startsWith("flight,airline,type"), "flights export header is correct");
        Path summary = ex.exportSummary(t.statistics(), "PRIORITY_FUEL", t.getMinute(), dir.resolve("summary.csv"));
        String text = Files.readString(summary);
        c.that(text.contains("landings," + t.statistics().getLandings()), "summary export has the landing count");
        c.that(text.contains("hour,movements"), "summary export has movements per hour");
    }
}
