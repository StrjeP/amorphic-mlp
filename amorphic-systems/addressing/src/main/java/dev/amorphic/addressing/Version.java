package dev.amorphic.addressing;

import java.util.Arrays;

/**
 * A version: a scheme value (spec 01, section 5). There is one scheme for now, {@value #DOTTED}, which is
 * also the default: numbers separated by dots, compared part by part, with a missing part read as 0.
 *
 * <p>Equality is by text, so {@code 2.1} and {@code 2.1.0} are different versions that compare as equal.
 * An address always prints the version exactly as it was written.
 */
public final class Version implements Comparable<Version> {

    /** The default scheme: dotted numbers. */
    public static final String DOTTED = "dotted";

    private final String text;
    private final int[] parts;

    private Version(String text, int[] parts) {
        this.text = text;
        this.parts = parts;
    }

    /** Reads a version under the default scheme. */
    public static Version parse(String text) {
        return new Version(text, numbers(text, "version"));
    }

    /** Reads dotted numbers, e.g. {@code 2.1} or {@code 1.0.3}; {@code what} names the thing in errors. */
    static int[] numbers(String text, String what) {
        if (text == null || text.isEmpty()) {
            throw new AddressException("a " + what + " can't be empty (expected dotted numbers, e.g. 2.1)");
        }
        String[] split = text.split("\\.", -1);
        int[] out = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            if (!split[i].matches("[0-9]{1,9}")) {
                throw new AddressException("'" + text + "' is not a valid " + what
                        + " (expected dotted numbers, e.g. 2.1)");
            }
            out[i] = Integer.parseInt(split[i]);
        }
        return out;
    }

    public String scheme() {
        return DOTTED;
    }

    /** Part {@code i}, counting from 0; a missing part is 0. */
    public int part(int i) {
        return i < parts.length ? parts[i] : 0;
    }

    public int size() {
        return parts.length;
    }

    @Override
    public int compareTo(Version other) {
        for (int i = 0; i < Math.max(parts.length, other.parts.length); i++) {
            int c = Integer.compare(part(i), other.part(i));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && v.text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }

    int[] parts() {
        return Arrays.copyOf(parts, parts.length);
    }
}
