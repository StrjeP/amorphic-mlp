package dev.amorphic.demo.pricing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A PURL-style capability address, e.g. pkg:amorphic/pricing-calc@2.1?lang=java. */
public record Address(String raw, String name, String version, Map<String, String> qualifiers) {

    private static final Pattern PURL = Pattern.compile("^pkg:amorphic/([^@?]+)@([^?]+)(?:\\?(.*))?$");

    public static Address parse(String raw) {
        Matcher m = PURL.matcher(raw == null ? "" : raw);
        if (!m.matches()) {
            throw new AddressTableException("'" + raw + "' is not a valid address (expected pkg:amorphic/<name>@<version>?lang=<lang>)");
        }
        Map<String, String> qualifiers = new LinkedHashMap<>();
        if (m.group(3) != null) {
            for (String pair : m.group(3).split("&")) {
                String[] kv = pair.split("=", 2);
                qualifiers.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return new Address(raw, m.group(1), m.group(2), Map.copyOf(qualifiers));
    }

    public String lang() {
        return qualifiers.getOrDefault("lang", "");
    }

    @Override
    public String toString() {
        return raw;
    }
}
