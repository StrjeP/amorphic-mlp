package dev.amorphic.demo;

import java.security.Security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        // Services in this demo are started, stopped and replaced while the app runs, and may come back
        // on a new IP. Keep DNS answers briefly so routing and /health probes see the change quickly.
        // (JVM defaults: 30s for answers, 10s for failed lookups.)
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
        SpringApplication.run(DemoApplication.class, args);
    }
}
