package dev.amorphic.demo.pricing;

/** The implementation rejected the input (bad amount, unsupported pair). Maps to HTTP 400. */
public class PricingRejectedException extends RuntimeException {
    public PricingRejectedException(String message) {
        super(message);
    }
}
