package it.flaviosimonelli.isw2.oldutil;

import java.util.Comparator;

/**
 * Utility class for comparing software version strings.
 * <p>
 * Supports common formats such as:
 * "1.2.3", "v4.5.10", "release-2.0", "4_6_0", etc.
 * Non-numeric parts are ignored.
 */
public final class VersionUtils {

    private VersionUtils() {
        // prevent instantiation
    }

    /**
     * Returns a comparator that compares version strings numerically.
     */
    public static Comparator<String> comparator() {
        return VersionUtils::compareVersions;
    }

    /**
     * Compares two version strings (e.g. "4.5.1" < "4.5.10" < "5.0").
     * Non-numeric characters are ignored.
     *
     * @param v1 first version string
     * @param v2 second version string
     * @return negative if v1 < v2, positive if v1 > v2, zero if equal
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return 1;  // nulls last
        if (v2 == null) return -1;

        // Normalize: keep only digits and dots
        v1 = normalize(v1);
        v2 = normalize(v2);

        if (v1.isEmpty() && v2.isEmpty()) return 0;
        if (v1.isEmpty()) return 1;
        if (v2.isEmpty()) return -1;

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = (i < parts1.length) ? parseSafe(parts1[i]) : 0;
            int n2 = (i < parts2.length) ? parseSafe(parts2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static String normalize(String s) {
        // Replace all non-numeric chars with dot, compress multiple dots
        return s.replaceAll("[^0-9.]", ".")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^\\.|\\.$", "");
    }

    private static int parseSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
