package dev.amorphic.composer;

/**
 * A plan that couldn't be made or run: no recipe for the outcome, no route for a step, or a step whose binding
 * couldn't reach what it binds to. A step that answers with an error is not this: that ends the plan with an error
 * reply ({@link Run#failure()}).
 */
public class CompositionException extends RuntimeException {

    public CompositionException(String message) {
        super(message);
    }

    public CompositionException(String message, Throwable cause) {
        super(message, cause);
    }
}
