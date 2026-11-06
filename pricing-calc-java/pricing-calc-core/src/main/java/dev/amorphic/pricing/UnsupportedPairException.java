package dev.amorphic.pricing;

public class UnsupportedPairException extends IllegalArgumentException {
    public UnsupportedPairException(String from, String to) {
        super("unsupported currency pair " + from + "->" + to);
    }
}
