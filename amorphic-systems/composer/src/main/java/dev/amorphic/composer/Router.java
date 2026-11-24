package dev.amorphic.composer;

import dev.amorphic.access.Invoker;
import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;

/**
 * Where the context's routing comes in (spec 04, section 5). The composer asks for a {@link Routing} when a plan
 * starts and chooses every step's {@link Route} from it, once, so all of a plan runs on one routing generation and
 * every call of a step goes the same way.
 */
@FunctionalInterface
public interface Router {

    Routing begin();

    /** A snapshot of the routing, for one plan. */
    interface Routing {

        /** The routing generation in force, for the call records (spec 03, section 4). */
        long generation();

        /**
         * The route for {@code step}: what its capability address resolves to, and how it is reached. Throws
         * {@link CompositionException} if nothing can serve it.
         */
        Route route(Recipe.Step step);
    }

    /**
     * How one step's calls go: the leg that serves (its answer is used) and, if there is one, the leg it is compared
     * with (called in the background, never used).
     */
    record Route(Leg served, Leg compared) {
        public Route {
            if (served == null) {
                throw new IllegalArgumentException("a route needs a leg that serves");
            }
        }

        public static Route of(Leg served) {
            return new Route(served, null);
        }
    }

    /**
     * One leg of a route. {@code role} is its part (champion, shadow, canary). {@code handle} is the binding URL, or
     * null for a local binding. {@code expected} is who the handshake found there, or for a local leg the part of
     * the app that runs it.
     */
    record Leg(String role, Address resolved, String binding, String handle, InstanceRef expected, boolean undeclared,
            Invoker invoker) {}
}
