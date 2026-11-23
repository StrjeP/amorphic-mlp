package dev.amorphic.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.BindingFactory;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.demo.pricing.ReloadablePricingCalc;
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

    /**
     * The startup table comes from amorphic.addresses. Later tables arrive through
     * POST /admin/address-table and are applied without a restart.
     */
    @Bean
    ReloadablePricingCalc pricingCalc(@Value("${amorphic.addresses}") String addresses,
            @Value("${amorphic.catalogue}") String catalogue, EvidenceRecorder evidence) {
        Path path = Path.of(addresses);
        return new ReloadablePricingCalc(AddressTable.load(path), path.toString(), BindingFactory.reading(() -> Catalogue.load(Path.of(catalogue))),
                Executors.newVirtualThreadPerTaskExecutor(), () -> ThreadLocalRandom.current().nextDouble(),
                evidence, Clock.systemUTC());
    }
}
