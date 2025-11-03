package org.github.tess1o.geopulse.geocoding.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class CountryMapper {

    private Map<String, String> mapping;

    @PostConstruct
    void init() {
        try (InputStream is = getClass().getResourceAsStream("/country-mapper.json")) {
            ObjectMapper mapper = new ObjectMapper();
            mapping = mapper.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to load country mapping", e);
            mapping = Collections.emptyMap();
        }
    }

    public String normalize(String countryName) {
        if (countryName == null) return null;
        if (mapping.isEmpty()) return countryName.trim();
        return mapping.getOrDefault(countryName.trim(), countryName.trim());
    }
}