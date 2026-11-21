package model;

/** Normal gates fit narrowbodies and business jets; large gates fit everything. */
public enum GateSize {
    NORMAL, LARGE;

    /** True if a gate of this size can take an aircraft that needs the given size. */
    public boolean fits(GateSize needed) {
        return this == LARGE || needed == NORMAL;
    }
}
