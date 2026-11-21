package exceptions;

/** Parent of every custom exception in SkyControl, so callers can catch them all in one place. */
public class SkyControlException extends Exception {
    public SkyControlException(String message) {
        super(message);
    }
}
