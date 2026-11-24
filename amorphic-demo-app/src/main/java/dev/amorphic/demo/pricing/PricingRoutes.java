package dev.amorphic.demo.pricing;

import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.AddressException;
import dev.amorphic.composer.Router;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pricing-calc's roles from one address table, made into routes for the composer.
 *
 * <ul>
 *   <li>champion: its answer is served.</li>
 *   <li>shadow: called in the background with the same request; compared, never returned.</li>
 *   <li>canary: serves {@code weight}% of plans; on those the champion is called in the background as the
 *       comparison, so Java-served answers are still checked.</li>
 * </ul>
 *
 * The canary's share is decided once per plan (spec 04, section 5): every conversion in one account report goes the
 * same way. A quote is a plan of one step, so it behaves as a single call always has.
 */
public final class PricingRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(PricingRoutes.class);

    record Target(Address address, String binding, Bound bound) {
        Router.Leg leg(String role) {
            return new Router.Leg(role, address, binding, bound.handle(), bound.instance(), bound.undeclared(),
                    bound.invoker());
        }

        String describe() {
            return address + " (" + binding + ")";
        }
    }

    private final Target champion;
    private final Target shadow;
    private final Target canary;
    private final int canaryWeight;
    private final DoubleSupplier random;

    private PricingRoutes(Target champion, Target shadow, Target canary, int canaryWeight, DoubleSupplier random) {
        this.champion = champion;
        this.shadow = shadow;
        this.canary = canary;
        this.canaryWeight = canaryWeight;
        this.random = random;
    }

    /** Resolves the capability's roles to endpoints. Throws {@link AddressTableException} if the table can't be honoured. */
    public static PricingRoutes resolve(AddressTable.Capability capability,
            BiFunction<Address, AddressTable.Binding, Bound> bindings, DoubleSupplier random) {
        if (capability.shadow() != null && capability.canary() != null) {
            throw new AddressTableException("a capability can have a shadow or a canary, not both");
        }
        Target champion = target(capability.champion(), bindings);
        Target shadow = capability.shadow() == null ? null : target(capability.shadow(), bindings);
        Target canary = null;
        int weight = 0;
        if (capability.canary() != null) {
            Integer w = capability.canary().weight();
            if (w == null || w < 0 || w > 100) {
                throw new AddressTableException("canary weight must be 0-100, got " + w);
            }
            canary = target(capability.canary(), bindings);
            weight = w;
        }
        return new PricingRoutes(champion, shadow, canary, weight, random);
    }

    private static Target target(AddressTable.Role role, BiFunction<Address, AddressTable.Binding, Bound> bindings) {
        if (role.address() == null) {
            throw new AddressTableException("a role in the address table has no address");
        }
        Address address;
        try {
            address = Address.parse(role.address());
        } catch (AddressException e) {
            throw new AddressTableException(e.getMessage());
        }
        return new Target(address, role.binding() == null ? null : role.binding().type(),
                bindings.apply(address, role.binding()));
    }

    String describe() {
        StringBuilder sb = new StringBuilder("champion=").append(champion.describe());
        if (shadow != null) sb.append(", shadow=").append(shadow.describe());
        if (canary != null) sb.append(", canary=").append(canary.describe()).append(" weight=").append(canaryWeight).append('%');
        return sb.toString();
    }

    /** The route for one plan: the canary or the champion serves, and the other role, if any, is compared. */
    public Router.Route route() {
        boolean useCanary = canary != null && random.getAsDouble() * 100 < canaryWeight;
        if (useCanary) {
            return new Router.Route(canary.leg("canary"), champion.leg("champion"));
        }
        return new Router.Route(champion.leg("champion"), shadow == null ? null : shadow.leg("shadow"));
    }

    void logResolved(long generation) {
        LOG.info("pricing-calc resolved (generation {}): {}", generation, describe());
    }
}
