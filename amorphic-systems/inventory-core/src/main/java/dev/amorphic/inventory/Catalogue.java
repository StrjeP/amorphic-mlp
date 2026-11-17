package dev.amorphic.inventory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.Candidate;
import dev.amorphic.addressing.Preference;
import dev.amorphic.addressing.Resolution;
import dev.amorphic.addressing.Resolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The static catalogue (catalogue.yaml): every version of every capability and piece of tooling,
 * and where it may run.
 */
public record Catalogue(Map<String, Capability> capabilities) {

    /** {@code kind} is "capability" (routed business work) or "tooling" (catalogue, inventory, governor). */
    public record Capability(String kind, String description, List<Version> versions) {
        public boolean isTooling() {
            return "tooling".equals(kind);
        }
    }

    /** {@code planned} is set for a version that isn't built yet, saying when it is due. */
    public record Version(Address address, List<String> locations, Service service, String withdrawn, String planned)
            implements Candidate {
        @Override
        public boolean isWithdrawn() {
            return withdrawn != null && !withdrawn.isBlank();
        }

        @Override
        public boolean isPlanned() {
            return planned != null && !planned.isBlank();
        }

        public boolean allows(String bindingType) {
            return locations != null && locations.contains(bindingType);
        }
    }

    public record Service(String name, String url) {}

    /** Reads each version's address when the catalogue loads, so a bad address fails the load. */
    private static final SimpleModule ADDRESSES = new SimpleModule().addDeserializer(Address.class,
            new JsonDeserializer<>() {
                @Override
                public Address deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                    return Address.parse(p.getValueAsString());
                }
            });

    public static Catalogue load(Path path) {
        try {
            Catalogue c = new ObjectMapper(new YAMLFactory())
                    .registerModule(ADDRESSES)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readValue(Files.readString(path), Catalogue.class);
            if (c == null || c.capabilities() == null) {
                throw new IllegalArgumentException("catalogue " + path + " has no 'capabilities'");
            }
            return c;
        } catch (java.nio.file.NoSuchFileException e) {
            throw new IllegalArgumentException("cannot read catalogue " + path + ": file not found", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read catalogue " + path + ": " + e.getMessage(), e);
        }
    }

    public String kind(String capability) {
        Capability c = capabilities.get(capability);
        return c == null || c.kind() == null ? "capability" : c.kind();
    }

    public List<Version> versions(String capability) {
        Capability c = capabilities.get(capability);
        return c == null || c.versions() == null ? List.of() : c.versions();
    }

    public Optional<Version> find(String capability, Address address) {
        return versions(capability).stream().filter(v -> v.address().equals(address)).findFirst();
    }

    /**
     * Chooses the catalogued version a request asks for (spec 01, best match): the highest that matches, skipping
     * withdrawn and planned versions unless one is asked for exactly.
     */
    public Resolution<Version> resolve(Address request) {
        return resolve(request, v -> true);
    }

    /** As {@link #resolve(Address)}, among only the versions that meet the caller's own condition. */
    public Resolution<Version> resolve(Address request, Predicate<Version> where) {
        Collection<Version> all = capabilities.values().stream()
                .flatMap(c -> c.versions() == null ? Stream.<Version>empty() : c.versions().stream())
                .filter(where)
                .toList();
        return Resolver.resolve(request, all, Preference.HIGHEST_VERSION);
    }
}
