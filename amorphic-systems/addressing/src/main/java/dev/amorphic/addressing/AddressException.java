package dev.amorphic.addressing;

/** An address, version or range that can't be read. The message says what was given and what was expected. */
public class AddressException extends IllegalArgumentException {

    public AddressException(String message) {
        super(message);
    }
}
