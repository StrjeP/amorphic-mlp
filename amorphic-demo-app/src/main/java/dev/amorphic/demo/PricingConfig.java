package dev.amorphic.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Instance;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.AddressTable;
import dev.amorphic.demo.pricing.AddressTableException;
import dev.amorphic.demo.pricing.BindingFactory;
import dev.amorphic.demo.pricing.MetaReader;
import dev.amorphic.demo.pricing.ReloadableRouting;
import dev.amorphic.composer.Composer;
import dev.amorphic.demo.compose.LocalCapabilities;
import dev.amorphic.demo.compose.Outcomes;
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
     * The routing the composer plans with. The startup table comes from amorphic.addresses. Later tables arrive
     * through POST /admin/address-table and are applied without a restart. Each is checked against what answers
     * (the handshake) before it is used. Positions and sum always run in the app.
     */
    @Bean
    ReloadableRouting routing(@Value("${amorphic.addresses}") String addresses,
            @Value("${amorphic.catalogue}") String catalogue, @Value("${amorphic.instances}") String instances,
            @Value("${amorphic.accounts}") String accounts, EvidenceRecorder evidence, Instance self, MetaReader meta) {
        Path path = Path.of(addresses);
        LocalCapabilities local = new LocalCapabilities(self, Path.of(accounts));
        return new ReloadableRouting(AddressTable.load(path), path.toString(),
                BindingFactory.reading(() -> Catalogue.load(Path.of(catalogue)), () -> Instances.load(Path.of(instances)),
                        self, meta),
                () -> ThreadLocalRandom.current().nextDouble(), evidence, Clock.systemUTC(), local::route);
    }

    /**
     * What the endpoints ask: an outcome, with its recipe read from the catalogue on every plan, so a hand edit applies
     * to the next one. Comparisons run in the background.
     */
    @Bean
    Outcomes outcomes(@Value("${amorphic.catalogue}") String catalogue, ReloadableRouting routing,
            EvidenceRecorder evidence, Instance self, TopologyLog topologies) {
        Composer composer = new Composer(() -> Catalogue.load(Path.of(catalogue)).outcomes(), routing,
                Executors.newVirtualThreadPerTaskExecutor(), topologies, InstanceRef.of(self), Clock.systemUTC());
        return new Outcomes(composer, evidence, Clock.systemUTC());
    }
}
