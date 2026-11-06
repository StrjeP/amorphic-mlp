package dev.amorphic.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.BindingFactory;
import dev.amorphic.demo.pricing.PricingCalc;
import dev.amorphic.demo.pricing.RoutingPricingCalc;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PricingConfig {

    @Bean
    EvidenceRecorder evidenceRecorder(ObjectMapper json) {
        return new EvidenceRecorder(json);
    }

    /** Loaded once at startup: a stage change is a restart with a new address table. */
    @Bean
    PricingCalc pricingCalc(@Value("${amorphic.addresses}") String addresses, EvidenceRecorder evidence) {
        AddressTable table = AddressTable.load(Path.of(addresses));
        return RoutingPricingCalc.resolve(table.capability("pricing-calc"), BindingFactory.DEFAULT,
                Executors.newVirtualThreadPerTaskExecutor(), () -> ThreadLocalRandom.current().nextDouble(),
                evidence, Clock.systemUTC());
    }
}
