package dev.amorphic.inventory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public record Version(String address, List<String> locations, Service service, String withdrawn, String planned) {
        public boolean isWithdrawn() {
            return withdrawn != null && !withdrawn.isBlank();
        }

        public boolean isPlanned() {
            return planned != null && !planned.isBlank();
        }

        public boolean allows(String bindingType) {
            return locations != null && locations.contains(bindingType);
        }
    }

    public record Service(String name, String url) {}

    public static Catalogue load(Path path) {
        try {
            Catalogue c = new ObjectMapper(new YAMLFactory())
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

    public Optional<Version> find(String capability, String address) {
        return versions(capability).stream().filter(v -> v.address().equals(address)).findFirst();
    }
}
