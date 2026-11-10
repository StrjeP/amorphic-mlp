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
            return parse(Files.readString(path), path.toString());
        } catch (IOException e) {
            throw new AddressTableException("cannot read address table " + path + ": " + e.getMessage());
        }
    }

    /** Parses and checks the shape of a table. {@code source} names it in error messages. */
    public static AddressTable parse(String yaml, String source) {
        if (yaml == null || yaml.isBlank()) {
            throw new AddressTableException("address table " + source + " is empty");
        }
        try {
            AddressTable table = new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readValue(yaml, AddressTable.class);
            if (table == null || table.capabilities() == null) {
                throw new AddressTableException("address table " + source + " has no 'capabilities'");
            }
            return table;
        } catch (IOException e) {
            throw new AddressTableException("cannot read address table " + source + ": " + e.getMessage());
        }
    }

    /** The tooling the address table may route besides capabilities: the inventory and the governor. */
    public static final java.util.Set<String> TOOLING = java.util.Set.of("inventory", "governor");

    /**
     * Checks the tooling entries, if any: a champion only (tooling isn't shadowed or canaried), bound
     * {@code local} (in-process in this app) or {@code remote} with a url.
     *
     * @throws AddressTableException if an entry can't be honoured
     */
    public void checkTooling() {
        for (String name : TOOLING) {
            Capability c = capabilities.get(name);
            if (c == null) {
                continue;
            }
            if (c.champion() == null || c.shadow() != null || c.canary() != null) {
                throw new AddressTableException("the " + name + " entry must have a champion and nothing else");
            }
            Binding b = c.champion().binding();
            String type = b == null ? null : b.type();
            if ("remote".equals(type)) {
                if (b.url() == null || b.url().isBlank()) {
                    throw new AddressTableException("remote binding for the " + name + " has no url");
                }
            } else if (!"local".equals(type)) {
                throw new AddressTableException("the " + name + " needs a binding of type local or remote, not '"
                        + type + "'");
            }
        }
    }

    /** The binding the table gives a tooling entry, or null if it has none (then it runs in-process). */
    public Binding tooling(String name) {
        Capability c = capabilities.get(name);
        return c == null || c.champion() == null ? null : c.champion().binding();
    }

    public Capability capability(String name) {
        Capability c = capabilities.get(name);
        if (c == null || c.champion() == null) {
            throw new AddressTableException("address table has no champion for capability '" + name + "'");
        }
        return c;
    }
}
