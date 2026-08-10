package org.github.tess1o.geopulse.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.dto.DemoPersonaResponse;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class DemoModeService {
    private static final String DEMO_USERS_RESOURCE_PATH = "/demo-users.json";

    @ConfigProperty(name = "geopulse.demo.enabled", defaultValue = "false")
    boolean demoModeEnabled;

    @ConfigProperty(name = "geopulse.demo.admin-read-only.enabled", defaultValue = "true")
    boolean demoAdminReadOnlyEnabled;

    private final ObjectMapper objectMapper;
    private final String demoUsersResourcePath;

    private volatile List<DemoPersona> personas = List.of();
    private volatile Map<String, DemoPersona> personasById = Map.of();

    @Inject
    public DemoModeService(ObjectMapper objectMapper) {
        this(objectMapper, DEMO_USERS_RESOURCE_PATH);
    }

    DemoModeService(ObjectMapper objectMapper, String demoUsersResourcePath) {
        this.objectMapper = objectMapper;
        this.demoUsersResourcePath = demoUsersResourcePath;
    }

    @PostConstruct
    void init() {
        personas = loadPersonas();
        personasById = indexPersonas(personas);
    }

    public boolean isEnabled() {
        return demoModeEnabled;
    }

    public boolean isAdminReadOnlyEnabled() {
        return demoAdminReadOnlyEnabled;
    }

    public List<DemoPersonaResponse> getPublicPersonas() {
        if (!demoModeEnabled) {
            return List.of();
        }

        return personas.stream()
                .map(persona -> new DemoPersonaResponse(persona.id(), persona.label(), persona.detail()))
                .toList();
    }

    public Optional<String> findPersonaEmail(String personaId) {
        if (!demoModeEnabled) {
            return Optional.empty();
        }

        if (personaId == null || personaId.isBlank()) {
            return Optional.empty();
        }

        DemoPersona persona = personasById.get(normalizeId(personaId));
        return Optional.ofNullable(persona).map(DemoPersona::email);
    }

    public boolean isDemoRestricted(SecurityIdentity identity) {
        return demoModeEnabled
                && identity != null
                && !identity.isAnonymous()
                && !identity.hasRole(SecurityRoles.ADMIN);
    }

    public boolean isDemoRestricted(UserEntity user) {
        return demoModeEnabled
                && user != null
                && user.getRole() != null
                && !SecurityRoles.ADMIN.equals(user.getRole().name());
    }

    public boolean canViewAdmin(UserEntity user) {
        return isAdmin(user) || (isDemoRestricted(user) && demoAdminReadOnlyEnabled);
    }

    public boolean isAdminReadOnly(UserEntity user) {
        return !isAdmin(user) && isDemoRestricted(user) && demoAdminReadOnlyEnabled;
    }

    private boolean isAdmin(UserEntity user) {
        return user != null
                && user.getRole() != null
                && SecurityRoles.ADMIN.equals(user.getRole().name());
    }

    private List<DemoPersona> loadPersonas() {
        JsonNode root = readResourceRoot();
        JsonNode usersNode = root.isArray() ? root : root.path("users");
        if (!usersNode.isArray()) {
            return List.of();
        }

        List<DemoPersona> parsedPersonas = new ArrayList<>();
        for (JsonNode userNode : usersNode) {
            parsePersona(userNode).ifPresent(parsedPersonas::add);
        }

        return List.copyOf(parsedPersonas);
    }

    private JsonNode readResourceRoot() {
        try (InputStream inputStream = getClass().getResourceAsStream(demoUsersResourcePath)) {
            if (inputStream == null) {
                log.warn("Bundled demo users file '{}' is missing", demoUsersResourcePath);
                return objectMapper.createObjectNode();
            }

            String rawContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(rawContent);
        } catch (IOException exception) {
            log.warn("Failed to load bundled demo users from '{}'", demoUsersResourcePath, exception);
            return objectMapper.createObjectNode();
        }
    }

    private Optional<DemoPersona> parsePersona(JsonNode userNode) {
        if (!userNode.isObject()) {
            return Optional.empty();
        }

        String id = textValue(userNode, "id");
        String label = textValue(userNode, "label");
        String detail = textValue(userNode, "detail");
        String email = textValue(userNode, "email");
        if (id == null || label == null || detail == null || email == null) {
            return Optional.empty();
        }

        return Optional.of(new DemoPersona(normalizeId(id), label, detail, email));
    }

    private Map<String, DemoPersona> indexPersonas(List<DemoPersona> loadedPersonas) {
        Map<String, DemoPersona> indexed = new LinkedHashMap<>();
        for (DemoPersona persona : loadedPersonas) {
            indexed.putIfAbsent(persona.id(), persona);
        }
        return Map.copyOf(indexed);
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (!valueNode.isTextual()) {
            return null;
        }

        String value = valueNode.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record DemoPersona(
            String id,
            String label,
            String detail,
            String email
    ) {
    }
}
