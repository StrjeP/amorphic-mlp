package dev.amorphic.envelope;

/** An envelope that can't be built or read, with the reason. */
public class EnvelopeException extends RuntimeException {
    public EnvelopeException(String message) {
        super(message);
    }

    public EnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
