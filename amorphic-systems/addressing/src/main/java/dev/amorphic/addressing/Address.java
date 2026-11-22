package dev.amorphic.addressing;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A logical address (spec 01): a kind, a name, an optional version or range, and qualifiers.
 *
 * <p>It is read and written in the PURL form {@code pkg:<kind>/<name>[@<version or range>][?<key>=<value>&...]},
 * e.g. {@code pkg:amorphic/greeter@1.0.1?lang=java}. The canonical form sorts qualifiers by key, so two equal
 * addresses always print the same. The kind is held as text: resolving it to its specification's address
 * (spec 01, section 4.1) comes later.
 *
 * <p>Where something runs is not part of its address: that is a binding, kept beside it. A running thing is an
 * {@link Instance}, which points to the address it realises.
 */
public final class Address {

    private static final Pattern PURL = Pattern.compile("^pkg:([a-z][a-z0-9.+-]*)/([^@?#/]+)(?:@([^?#]+))?(?:\\?([^#]*))?$");
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9._-]*");
    private static final String EXPECTED = "expected pkg:<kind>/<name>[@<version>][?<key>=<value>&...]";

    private final String kind;
    private final String name;
    private final Version version;
    private final Range range;
    private final SortedMap<String, String> qualifiers;

    private Address(String kind, String name, Version version, Range range, SortedMap<String, String> qualifiers) {
        this.kind = kind;
        this.name = name;
        this.version = version;
        this.range = range;
        this.qualifiers = Collections.unmodifiableSortedMap(new TreeMap<>(qualifiers));
    }

    public static Address parse(String text) {
        Matcher m = PURL.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new AddressException("'" + text + "' is not a valid address (" + EXPECTED + ")");
        }
        Version version = null;
        Range range = null;
        String v = m.group(3);
        try {
            if (v != null) {
                if (Range.isRange(v)) {
                    range = Range.parse(v);
                } else {
                    version = Version.parse(v);
                }
            }
        } catch (AddressException e) {
            throw new AddressException("'" + text + "' is not a valid address: " + e.getMessage());
        }
        SortedMap<String, String> qualifiers = new TreeMap<>();
        if (m.group(4) != null) {
            for (String pair : m.group(4).split("&", -1)) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2 || !KEY.matcher(kv[0]).matches() || kv[1].isEmpty()) {
                    throw new AddressException("'" + text + "' is not a valid address: qualifier '" + pair
                            + "' should be <key>=<value>");
                }
                if (qualifiers.put(kv[0], kv[1]) != null) {
                    throw new AddressException("'" + text + "' is not a valid address: qualifier '" + kv[0]
                            + "' appears twice");
                }
            }
        }
        return new Address(m.group(1), m.group(2), version, range, qualifiers);
    }

    /** The capability rung: a kind and a name alone. */
    public static Address of(String kind, String name) {
        return parse("pkg:" + kind + "/" + name);
    }

    public String kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public Optional<Version> version() {
        return Optional.ofNullable(version);
    }

    public Optional<Range> range() {
        return Optional.ofNullable(range);
    }

    public Map<String, String> qualifiers() {
        return qualifiers;
    }

    public Optional<String> qualifier(String key) {
        return Optional.ofNullable(qualifiers.get(key));
    }

    /** Pinned: names exactly one version. Otherwise floating (a range, or no version at all). */
    public boolean isPinned() {
        return version != null;
    }

    public Rung rung() {
        if (!qualifiers.isEmpty()) {
            return Rung.IMPLEMENTATION;
        }
        if (version != null) {
            return Rung.VERSION;
        }
        return range != null ? Rung.RANGE : Rung.CAPABILITY;
    }

    /** The same capability with a version or range in place of whatever this one has. */
    public Address at(String versionOrRange) {
        return parse("pkg:" + kind + "/" + name + "@" + versionOrRange + query(qualifiers));
    }

    public Address with(String key, String value) {
        SortedMap<String, String> q = new TreeMap<>(qualifiers);
        q.put(key, value);
        return parse("pkg:" + kind + "/" + name + versionPart() + query(q));
    }

    /**
     * Up one rung: an implementation drops its qualifiers, a version becomes the range around its first part
     * ({@code 2.1} to {@code 2.*}), and a range becomes the capability. A capability can't be broadened.
     */
    public Optional<Address> broaden() {
        return switch (rung()) {
            case IMPLEMENTATION -> Optional.of(new Address(kind, name, version, range, new TreeMap<>()));
            case VERSION -> Optional.of(new Address(kind, name, null, Range.around(version), new TreeMap<>()));
            case RANGE -> Optional.of(new Address(kind, name, null, null, new TreeMap<>()));
            case CAPABILITY -> Optional.empty();
        };
    }

    /**
     * True if {@code this} states everything {@code other} states, and at least one thing more. So
     * {@code greeter@1.0.1?lang=java} narrows {@code greeter@1.0.1}, which narrows {@code greeter@1.*},
     * which narrows {@code greeter}.
     */
    public boolean narrows(Address other) {
        return within(other) && !equals(other);
    }

    /** True if this address states everything {@code other} states (equal, or narrower). */
    public boolean within(Address other) {
        if (!kind.equals(other.kind) || !name.equals(other.name)) {
            return false;
        }
        if (other.version != null && !other.version.equals(version)) {
            return false;
        }
        if (other.range != null) {
            boolean ok = version != null ? other.range.contains(version) : range != null && range.within(other.range);
            if (!ok) {
                return false;
            }
        }
        return qualifiers.entrySet().containsAll(other.qualifiers.entrySet());
    }

    /**
     * True if {@code candidate} is one of the things this request asks for: the same kind and name, a version
     * equal to the one asked for or inside the range asked for, and every qualifier asked for, matching exactly.
     */
    public boolean matches(Address candidate) {
        return candidate.version != null && candidate.within(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Address a && a.kind.equals(kind) && a.name.equals(name)
                && Objects.equals(a.version, version) && Objects.equals(a.range, range)
                && a.qualifiers.equals(qualifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, version, range, qualifiers);
    }

    /** The canonical form. */
    @Override
    public String toString() {
        return "pkg:" + kind + "/" + name + versionPart() + query(qualifiers);
    }

    private String versionPart() {
        if (version != null) {
            return "@" + version;
        }
        return range != null ? "@" + range : "";
    }

    private static String query(Map<String, String> q) {
        return q.isEmpty() ? "" : "?" + q.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }
}
