package dev.amorphic.demo.pricing;

/** A problem with the address table that must stop the app from starting. */
public class AddressTableException extends RuntimeException {
    public AddressTableException(String message) {
        super(message);
    }
}
