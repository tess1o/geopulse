package org.github.tess1o.geopulse.geocoding.service.external;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for validating/sanitizing language values passed to Photon.
 *
 * Photon commonly expects simple language tags such as "en" or "de".
 */
public final class PhotonLanguageValidator {

    public static final Set<String> ALLOWED_PHOTON_LANGUAGES = Set.of(
            "de", "pl", "el", "en", "es", "fa", "fr", "it", "ja", "ko"
    );
    public static final String COMMON_LANGUAGE_EXAMPLES = "de, pl, el, en, es, fa, fr, it, ja, ko";
    private static final Pattern PREFIX_LANGUAGE_HINT = Pattern.compile("^([a-z]{2,3})[-_].*$");

    private PhotonLanguageValidator() {
        // Utility class.
    }

    public static boolean isValidPhotonLanguage(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        return ALLOWED_PHOTON_LANGUAGES.contains(normalized);
    }

    public static String sanitizeForPhoton(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return isValidPhotonLanguage(normalized) ? normalized : null;
    }

    public static Optional<String> suggestClosestLanguage(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = PREFIX_LANGUAGE_HINT.matcher(normalized);
        if (matcher.matches()) {
            String candidate = matcher.group(1);
            if (ALLOWED_PHOTON_LANGUAGES.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        String closest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : ALLOWED_PHOTON_LANGUAGES) {
            int distance = levenshteinDistance(normalized, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = candidate;
            }
        }
        if (closest != null && bestDistance <= 2) {
            return Optional.of(closest);
        }
        return Optional.empty();
    }

    private static int levenshteinDistance(String left, String right) {
        int[][] matrix = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            matrix[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            matrix[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                matrix[i][j] = Math.min(
                        Math.min(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1),
                        matrix[i - 1][j - 1] + cost
                );
            }
        }
        return matrix[left.length()][right.length()];
    }
}
