package dev.amorphic.access;

/** A binding that couldn't reach what it binds to, or got back something it couldn't read. Not a failure reply. */
public class AccessException extends RuntimeException {
    public AccessException(String message) {
        super(message);
    }

    public AccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
