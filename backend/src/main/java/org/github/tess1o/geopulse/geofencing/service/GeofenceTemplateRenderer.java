package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class GeofenceTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9]*)\\s*}}");

    public String render(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return "";
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.containsKey(key)
                    ? (values.get(key) == null ? "" : values.get(key))
                    : matcher.group(0);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
