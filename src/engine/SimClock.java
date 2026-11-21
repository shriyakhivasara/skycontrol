package engine;

/**
 * The simulation clock: a background thread that advances the control tower one simulated
 * minute at a time. At speed 1, one real second is one simulated minute.
 */
public class SimClock implements Runnable {
    private final ControlTower tower;
    private volatile int speed = 1;          // simulated minutes per real second
    private volatile boolean paused = false;
    private volatile boolean running = false;
    private Thread thread;

    public SimClock(ControlTower tower) {
        this.tower = tower;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this, "sim-clock");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(Math.max(10, 1000 / speed));
            } catch (InterruptedException e) {
                return;
            }
            if (!paused) {
                try {
                    tower.tick();
                } catch (RuntimeException e) {
                    // never let one bad minute kill the clock
                    System.err.println("Clock error at minute " + tower.getMinute() + ": " + e);
                }
            }
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Sets the speed in simulated minutes per real second (1 to 30). */
    public void setSpeed(int minutesPerSecond) {
        this.speed = Math.max(1, Math.min(30, minutesPerSecond));
    }

    public int getSpeed() { return speed; }
    public void pause() { paused = true; }
    public void resume() { paused = false; }
    public boolean isPaused() { return paused; }
}
