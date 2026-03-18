package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class GeofenceTemplateRenderer {

    public String render(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return "";
        }

        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
