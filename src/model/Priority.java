package model;

/** Landing/take-off priority. A higher rank goes first under the priority policy. */
public enum Priority {
    NORMAL(0), LOW_FUEL(1), EMERGENCY(2);

    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
