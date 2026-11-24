package dev.amorphic.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import dev.amorphic.composer.Recipe;
import dev.amorphic.envelope.Schemas;
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
 * The static catalogue (catalogue.yaml): every version of every capability and piece of tooling, where it may run
 * and how it is reached, the outcomes callers can ask for with the recipe for each (spec 04), and the schemas their
 * envelopes use (spec 02).
 */
public record Catalogue(Map<String, Capability> capabilities, List<Recipe> outcomes, List<Schema> schemas,
        @JsonProperty("accept-undeclared") Boolean acceptUndeclared) {

    public Catalogue {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        schemas = schemas == null ? List.of() : List.copyOf(schemas);
    }

    public Catalogue(Map<String, Capability> capabilities, List<Schema> schemas, Boolean acceptUndeclared) {
        this(capabilities, List.of(), schemas, acceptUndeclared);
    }

    public Catalogue(Map<String, Capability> capabilities, List<Schema> schemas) {
        this(capabilities, schemas, null);
    }

    public Catalogue(Map<String, Capability> capabilities) {
        this(capabilities, List.of());
    }

    /**
     * Whether something may answer without being declared first (spec 03, section 5). If so, it is recorded as
     * undeclared; if not, routing to it is refused. Off unless the catalogue says {@code accept-undeclared: true}.
     */
    public boolean acceptsUndeclared() {
        return Boolean.TRUE.equals(acceptUndeclared);
    }

    /** {@code kind} is "capability" (routed business work) or "tooling" (catalogue, inventory, governor). */
    public record Capability(String kind, String description, List<Version> versions) {
        public boolean isTooling() {
            return "tooling".equals(kind);
        }
    }

    /**
     * {@code planned} is set for a version that isn't built yet, saying when it is due. {@code access} says how a
     * remote version is reached: it speaks the envelope, or it is wrapped by an adapter.
     */
    public record Version(Address address, List<String> locations, Service service, String withdrawn, String planned,
            Access access) implements Candidate {

        public Version(Address address, List<String> locations, Service service, String withdrawn, String planned) {
            this(address, locations, service, withdrawn, planned, null);
        }

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

    /** How a version is reached (spec 02, section 7): {@value #ENVELOPE}, or through an {@value #ADAPTER}. */
    public record Access(String via, Adapter adapter) {
        public static final String ENVELOPE = "envelope";
        public static final String ADAPTER = "adapter";

        public Access {
            if (!ENVELOPE.equals(via) && !ADAPTER.equals(via)) {
                throw new IllegalArgumentException("access via '" + via + "' is not known (expected " + ENVELOPE
                        + " or " + ADAPTER + ")");
            }
            if (ADAPTER.equals(via) && adapter == null) {
                throw new IllegalArgumentException("access via adapter needs an 'adapter' saying how to map the envelope");
            }
        }

        public boolean viaAdapter() {
            return ADAPTER.equals(via);
        }
    }

    /**
     * How an adapter maps an envelope onto a plain API: the method and path, which payload fields become query
     * parameters ({@code query}: payload field to parameter), which response fields become the reply payload
     * ({@code reply}: response field to payload field), and the reply payload's schema.
     */
    public record Adapter(String method, String path, Map<String, String> query, Map<String, String> reply,
            Address schema) {}

    /**
     * A schema: an addressed asset (spec 02, section 8). {@code file} is relative to the catalogue when listed, and
     * absolute once loaded. The framework's own schemas are listed too, and read from its jar.
     */
    public record Schema(Address address, String file) implements Candidate {
        static final String BUILT_IN = "built-in";

        @Override
        public boolean isWithdrawn() {
            return false;
        }

        @Override
        public boolean isPlanned() {
            return false;
        }

        public boolean isBuiltIn() {
            return BUILT_IN.equals(file);
        }

        /** The schema's text. */
        public String text() {
            if (isBuiltIn()) {
                return Schemas.read(address);
            }
            try {
                return Files.readString(Path.of(file));
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot read schema " + address + " from " + file + ": " + e.getMessage(), e);
            }
        }
    }

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
            return new Catalogue(c.capabilities(), c.outcomes(), located(c.schemas(), path), c.acceptUndeclared());
        } catch (java.nio.file.NoSuchFileException e) {
            throw new IllegalArgumentException("cannot read catalogue " + path + ": file not found", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read catalogue " + path + ": " + e.getMessage(), e);
        }
    }

    /** Each listed schema's file made absolute, checking it exists, so a missing file fails the load. */
    private static List<Schema> located(List<Schema> schemas, Path catalogue) {
        Path dir = catalogue.toAbsolutePath().getParent();
        return schemas.stream().map(s -> {
            if (s.address() == null || s.file() == null) {
                throw new IllegalArgumentException("catalogue " + catalogue + ": every schema needs an address and a file");
            }
            Path file = dir.resolve(s.file()).normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("catalogue " + catalogue + ": schema " + s.address()
                        + " points to " + s.file() + ", which doesn't exist");
            }
            return new Schema(s.address(), file.toString());
        }).toList();
    }

    /**
     * Chooses the schema a request asks for, among those listed and the framework's own, by the same best match as
     * versions (spec 01).
     */
    public Resolution<Schema> schema(Address request) {
        List<Schema> all = Stream.concat(schemas.stream(),
                Schemas.BUILT_IN.keySet().stream().map(a -> new Schema(a, Schema.BUILT_IN))).toList();
        return Resolver.resolve(request, all, Preference.HIGHEST_VERSION);
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
