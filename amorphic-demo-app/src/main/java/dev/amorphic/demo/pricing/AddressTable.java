package dev.amorphic.demo.pricing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** The address table: which address plays which role for each capability, and how it is reached. */
public record AddressTable(Map<String, Capability> capabilities) {

    public record Capability(Role champion, Role shadow, Role canary) {}

    public record Role(String address, Binding binding, Integer weight) {}

    public record Binding(String type, String url) {}

    public static AddressTable load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new AddressTableException("address table not found at " + path.toAbsolutePath());
        }
        try {
            AddressTable table = new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readValue(path.toFile(), AddressTable.class);
            if (table == null || table.capabilities() == null) {
                throw new AddressTableException("address table " + path + " has no 'capabilities'");
            }
            return table;
        } catch (IOException e) {
            throw new AddressTableException("cannot read address table " + path + ": " + e.getMessage());
        }
    }

    public Capability capability(String name) {
        Capability c = capabilities.get(name);
        if (c == null || c.champion() == null) {
            throw new AddressTableException("address table has no champion for capability '" + name + "'");
        }
        return c;
    }
}
