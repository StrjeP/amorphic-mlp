package dev.amorphic.inventory;

/** The result of probing one bound address. */
public record HealthCheck(Status status, String target, Long latencyMs, String detail) {

    public enum Status {
        /** GET {url}/health answered 200. */
        UP,
        /** No answer, a non-200 answer, or no answer in time. */
        DOWN,
        /** A local binding: it runs inside the app, so it is up whenever the app is. */
        IN_PROCESS
    }

    public boolean healthy() {
        return status != Status.DOWN;
    }

    public static HealthCheck inProcess() {
        return new HealthCheck(Status.IN_PROCESS, null, null, "runs inside the app");
    }
}
