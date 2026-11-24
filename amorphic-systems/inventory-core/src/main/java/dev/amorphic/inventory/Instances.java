package dev.amorphic.inventory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.amorphic.addressing.Address;
import dev.amorphic.addressing.AddressException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The declared instances (instances.yaml), the inventory's half of the catalogue (spec 03, section 5). The system
 * allocates an identity when it declares an instance, ahead of it appearing: each entry is an identity, the address
 * it realises, and its handle (how it is reached now). Two instances can share a handle at different times.
 */
public record Instances(List<Declared> declared) {

    /** One declared instance. {@code handle} is a binding URL, or null for one that isn't reached remotely. */
    public record Declared(String identity, Address realises, String handle) {}

    public Instances {
        declared = List.copyOf(declared);
    }

    public static Instances load(Path path) {
        JsonNode root;
        try {
            root = new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readTree(Files.readString(path));
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("cannot read instances " + path + ": file not found", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read instances " + path + ": " + e.getMessage(), e);
        }
        if (root == null || !root.path("instances").isArray()) {
            throw new IllegalArgumentException("instances " + path + " has no 'instances' list");
        }
        List<Declared> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode n : root.path("instances")) {
            String identity = n.path("identity").asText("");
            if (identity.isBlank()) {
                throw new IllegalArgumentException("instances " + path + ": every instance needs an identity");
            }
            if (!seen.add(identity)) {
                throw new IllegalArgumentException("instances " + path + ": " + identity + " is declared twice");
            }
            Address realises;
            try {
                realises = Address.parse(n.path("realises").asText(""));
            } catch (AddressException e) {
                throw new IllegalArgumentException("instances " + path + ": " + identity + " realises an address that can't be read: "
                        + e.getMessage(), e);
            }
            String handle = n.hasNonNull("handle") ? trim(n.get("handle").asText()) : null;
            out.add(new Declared(identity, realises, handle));
        }
        return new Instances(out);
    }

    public Optional<Declared> identity(String identity) {
        return declared.stream().filter(d -> d.identity().equals(identity)).findFirst();
    }

    /** What is declared to realise {@code address} at {@code handle}, if anything. */
    public Optional<Declared> at(String handle, Address address) {
        String h = trim(handle);
        return declared.stream().filter(d -> h.equals(d.handle()) && d.realises().equals(address)).findFirst();
    }

    static String trim(String url) {
        return url == null ? null : url.replaceAll("/+$", "");
    }
}
