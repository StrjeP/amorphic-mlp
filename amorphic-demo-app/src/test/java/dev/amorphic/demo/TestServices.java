package dev.amorphic.demo;

import dev.amorphic.demo.pricing.MetaReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The app's tests run without the pricing services, so the handshake asks this instead of each service's /meta: it
 * answers as the instance declared in amorphic-demo-env/instances.yaml for that handle and address.
 */
@Configuration
class TestServices {

    @Bean
    @Primary
    MetaReader declaredMeta() {
        return dev.amorphic.demo.pricing.StageAddressTablesTestAccess.DECLARED_META;
    }
}
