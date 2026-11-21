package data;

import engine.FlightListener;
import engine.LogEntry;
import exceptions.StorageException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import model.Flight;
import model.FlightSnapshot;

/**
 * Producer-consumer: the control tower (producer) reports every event; this class copies
 * it straight away and puts it on a queue, and a separate background thread (consumer)
 * writes it to the database. The simulation never waits for the disk.
 */
public class AsyncStorageWriter implements FlightListener {
    private final Storage storage;
    private final int runId;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;
    private volatile String lastError;
    private volatile int written = 0;

    public AsyncStorageWriter(Storage storage, int runId) {
        this.storage = storage;
        this.runId = runId;
        this.worker = new Thread(this::drain, "storage-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Called by the tower (on the clock thread). Copies the data, never blocks. */
    @Override
    public void onFlightEvent(Flight flight, String message, int minute) {
        queue.add(new LogEntry(minute, flight == null ? null : flight.getFlightNumber(), message));
        if (flight != null) {
            queue.add(FlightSnapshot.of(flight));
        }
    }

    private void drain() {
        while (running || !queue.isEmpty()) {
            try {
                Object item = queue.poll(200, TimeUnit.MILLISECONDS);
                if (item == null) {
                    continue;
                }
                if (item instanceof LogEntry e) {
                    storage.saveEvent(runId, e);
                } else if (item instanceof FlightSnapshot f) {
                    storage.saveFlight(runId, f);
                }
                written++;
            } catch (InterruptedException e) {
                return;
            } catch (StorageException e) {
                lastError = e.getMessage();
            }
        }
    }

    /** Waits until everything queued so far has been written (or the timeout passes). */
    public void flush(long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (!queue.isEmpty() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Writes what is left, then stops the background thread. */
    public void stop() {
        running = false;
        try {
            worker.join(5000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public int getRunId() { return runId; }
    public int getWritten() { return written; }
    public int getPending() { return queue.size(); }
    public String getLastError() { return lastError; }
}
