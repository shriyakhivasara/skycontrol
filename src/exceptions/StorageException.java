package exceptions;

/** Thrown when the database cannot be opened, read or written. */
public class StorageException extends SkyControlException {
    public StorageException(String message, Throwable cause) {
        super(message + (cause == null ? "" : ": " + cause.getMessage()));
        if (cause != null) {
            initCause(cause);
        }
    }
}
