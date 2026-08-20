package org.github.tess1o.geopulse.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.auth.dto.DemoPersonaResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DemoModeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getPublicPersonas_UsesBundledDemoUsersWithoutEmail() {
        DemoModeService service = new DemoModeService(objectMapper, "/demo-users.json");
        service.demoModeEnabled = true;
        service.init();

        List<DemoPersonaResponse> personas = service.getPublicPersonas();

        DemoPersonaResponse newYork = personas.get(1);

        assertEquals(3, personas.size());
        assertEquals("new-york", newYork.id());
        assertEquals("🗽 Login as New York",newYork.label());
        assertEquals("(Miles, 12-hour clock, US date format)", newYork.detail());
        assertEquals(Optional.of("new-york@demo.geopulse.cc"), service.findPersonaEmail("NEW-YORK"));
        assertEquals("America/New_York", service.getProvisioningPersonas().get(1).timezone());
        assertEquals("MILES", service.getProvisioningPersonas().get(1).distanceUnit().name());
        assertEquals("FAHRENHEIT", service.getProvisioningPersonas().get(1).temperatureUnit().name());
        assertEquals("MDY", service.getProvisioningPersonas().get(1).dateFormat());
        assertEquals("12h", service.getProvisioningPersonas().get(1).timeFormat());

        JsonNode serializedPersona = objectMapper.valueToTree(newYork);
        assertFalse(serializedPersona.has("email"));
    }

    @Test
    void getPublicPersonas_WhenDemoModeIsDisabled_ReturnsEmptyList() {
        DemoModeService service = new DemoModeService(objectMapper, "/demo-users.json");
        service.demoModeEnabled = false;
        service.init();

        assertTrue(service.getPublicPersonas().isEmpty());
        assertTrue(service.findPersonaEmail("new-york").isEmpty());
        assertTrue(service.getProvisioningPersonas().isEmpty());
    }

    @Test
    void getPublicPersonas_IgnoresInvalidEntriesAndKeepsValidOnes() {
        DemoModeService service = new DemoModeService(objectMapper, "/demo-users-invalid.json");
        service.demoModeEnabled = true;
        service.init();

        List<DemoPersonaResponse> personas = service.getPublicPersonas();

        assertEquals(1, personas.size());
        assertEquals("valid", personas.getFirst().id());
        assertEquals("Login as Valid", personas.getFirst().label());
        assertEquals("(Valid profile)", personas.getFirst().detail());
        assertEquals(Optional.of("valid-demo@example.com"), service.findPersonaEmail("valid"));
    }
}
