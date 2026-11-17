package dev.amorphic.addressing;

import java.util.Arrays;

/**
 * A set of versions, written as a wildcard (spec 01, section 5): {@code *} is any version, and
 * {@code 2.*} is any version whose first part is 2 ({@code 2}, {@code 2.1}, {@code 2.1.3}).
 * Comparisons such as {@code >=2.1 <3} may come later as another form.
 */
public final class Range {

    private final String text;
    private final int[] prefix;

    private Range(String text, int[] prefix) {
        this.text = text;
        this.prefix = prefix;
    }

    public static final Range ANY = new Range("*", new int[0]);

    public static Range parse(String text) {
        if ("*".equals(text)) {
            return ANY;
        }
        if (text == null || !text.endsWith(".*")) {
            throw new AddressException("'" + text + "' is not a valid range (expected a wildcard, e.g. 2.* or *)");
        }
        return new Range(text, Version.numbers(text.substring(0, text.length() - 2), "range"));
    }

    /** The range a version broadens to: its first part followed by {@code .*}. */
    static Range around(Version v) {
        return parse(v.part(0) + ".*");
    }

    static boolean isRange(String text) {
        return text.contains("*");
    }

    public boolean contains(Version v) {
        for (int i = 0; i < prefix.length; i++) {
            if (v.part(i) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** True if every version in {@code this} is also in {@code other}, e.g. {@code 2.1.*} within {@code 2.*}. */
    public boolean within(Range other) {
        if (other.prefix.length > prefix.length) {
            return false;
        }
        return Arrays.equals(prefix, 0, other.prefix.length, other.prefix, 0, other.prefix.length);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Range r && r.text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
