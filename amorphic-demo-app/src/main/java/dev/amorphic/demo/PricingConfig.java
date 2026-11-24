package dev.amorphic.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Instance;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.AddressTableException;
import dev.amorphic.demo.pricing.BindingFactory;
import dev.amorphic.demo.pricing.MetaReader;
import dev.amorphic.demo.pricing.ReloadablePricingCalc;
import dev.amorphic.envelope.InstanceRef;
import dev.amorphic.inventory.Catalogue;
import dev.amorphic.inventory.Instances;
import dev.amorphic.topology.TopologyLog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PricingConfig {

    /** What the app itself realises: its logical address (spec 01, section 8: a host has one too). */
    static final Address APP = Address.parse("pkg:amorphic/demo-app@0.1?lang=java");

    @Bean
    EvidenceRecorder evidenceRecorder(ObjectMapper json) {
        return new EvidenceRecorder(json);
    }

    /**
     * The app's own instance, by the identity it was declared with. It is the caller in every topology, and the host
     * of a folded-in core. An identity that isn't declared stops the app, unless the catalogue accepts that.
     */
    @Bean
    Instance self(@Value("${amorphic.instance}") String identity, @Value("${amorphic.instances}") String instances,
            @Value("${amorphic.catalogue}") String catalogue) {
        if (Instances.load(Path.of(instances)).identity(identity).isEmpty()
                && !Catalogue.load(Path.of(catalogue)).acceptsUndeclared()) {
            throw new AddressTableException("the app's identity " + identity + " isn't declared in " + instances
                    + ", and the catalogue doesn't accept undeclared instances");
        }
        return Instance.deployed(APP, identity, null);
    }

    @Bean
    TopologyLog topologyLog(@Value("${amorphic.topology:}") String file) throws java.io.IOException {
        if (file == null || file.isBlank()) {
            return TopologyLog.inMemory();
        }
        Path path = Path.of(file);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        return new TopologyLog(path);
    }

    @Bean
    MetaReader metaReader() {
        return MetaReader.HTTP;
    }

    /**
     * The startup table comes from amorphic.addresses. Later tables arrive through
     * POST /admin/address-table and are applied without a restart. Each is checked against what answers
     * (the handshake) before it is used.
     */
    @Bean
    ReloadablePricingCalc pricingCalc(@Value("${amorphic.addresses}") String addresses,
            @Value("${amorphic.catalogue}") String catalogue, @Value("${amorphic.instances}") String instances,
            EvidenceRecorder evidence, Instance self, TopologyLog topologies, MetaReader meta) {
        Path path = Path.of(addresses);
        return new ReloadablePricingCalc(AddressTable.load(path), path.toString(),
                BindingFactory.reading(() -> Catalogue.load(Path.of(catalogue)), () -> Instances.load(Path.of(instances)),
                        self, meta),
                Executors.newVirtualThreadPerTaskExecutor(), () -> ThreadLocalRandom.current().nextDouble(),
                evidence, Clock.systemUTC(), topologies, InstanceRef.of(self));
    }
}
