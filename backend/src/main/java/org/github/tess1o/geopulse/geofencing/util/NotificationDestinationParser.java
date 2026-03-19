package org.github.tess1o.geopulse.geofencing.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class NotificationDestinationParser {

    private static final Pattern DESTINATION_URL_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.+$");

    private NotificationDestinationParser() {
    }

    public static List<String> parseUrls(String destination) {
        if (destination == null || destination.isBlank()) {
            return List.of();
        }

        String[] lines = destination.split("\\R");
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.contains(",") || line.contains(";")) {
                throw new IllegalArgumentException("Destination line " + (i + 1)
                        + " must contain exactly one URL. Use one destination per line.");
            }
            if (!DESTINATION_URL_PATTERN.matcher(line).matches()) {
                throw new IllegalArgumentException("Destination line " + (i + 1)
                        + " must be a valid URL in the format scheme://...");
            }
            urls.add(line);
        }

        return List.copyOf(urls);
    }

    public static String normalize(String destination) {
        List<String> urls = parseUrls(destination);
        return urls.isEmpty() ? "" : String.join("\n", urls);
    }
}
