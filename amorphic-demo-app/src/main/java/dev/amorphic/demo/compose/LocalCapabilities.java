package dev.amorphic.demo.compose;

import dev.amorphic.access.Handler;
import dev.amorphic.access.LocalBinding;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Instance;
import dev.amorphic.composer.Recipe;
import dev.amorphic.composer.Router;
import dev.amorphic.envelope.InstanceRef;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The capabilities that always run inside the app: positions and sum. Each is a part of the app's own instance
 * (spec 01: the host's identity plus the part's name), reached by a local binding, so there is no service, stage
 * file or handshake for them.
 */
public final class LocalCapabilities {

    public static final Address POSITIONS = Address.parse("pkg:amorphic/positions@1.0?lang=java");
    public static final Address SUM = Address.parse("pkg:amorphic/sum@1.0?lang=java");

    private record Local(Address address, Router.Leg leg) {}

    private final List<Local> locals;

    public LocalCapabilities(Instance self, Path accounts) {
        this.locals = List.of(local(self, POSITIONS, new PositionsHandler(accounts)), local(self, SUM, new SumHandler()));
    }

    private static Local local(Instance self, Address address, Handler handler) {
        InstanceRef part = InstanceRef.of(Instance.part(self, address.name(), address));
        return new Local(address, new Router.Leg("champion", address, "local", null, part, false,
                new LocalBinding(handler)));
    }

    /** The route for a step one of these serves, or empty. */
    public Optional<Router.Route> route(Recipe.Step step) {
        return locals.stream().filter(l -> step.capability().matches(l.address())).findFirst()
                .map(l -> Router.Route.of(l.leg()));
    }
}
