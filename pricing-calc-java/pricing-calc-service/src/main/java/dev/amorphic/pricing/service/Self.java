package dev.amorphic.pricing.service;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.pricing.PricingCalcCore;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who this service is (spec 03, section 5). The identity is the one it was declared with, passed in by whoever
 * deployed it ({@code AMORPHIC_INSTANCE}); what it realises comes from the code itself, the core's own version, so
 * a lingering 2.0 can't claim to be 2.1 even if it is mislabelled. With no declared identity it says nothing,
 * rather than make one up.
 */
@Component
class Self {

    static final Address REALISES = Address.parse("pkg:amorphic/pricing-calc@" + PricingCalcCore.VERSION + "?lang=java");

    private final Optional<InstanceRef> instance;

    Self(@Value("${amorphic.instance:}") String identity) {
        this.instance = identity == null || identity.isBlank() ? Optional.empty()
                : Optional.of(new InstanceRef(identity, REALISES, null));
    }

    Optional<InstanceRef> instance() {
        return instance;
    }
}
