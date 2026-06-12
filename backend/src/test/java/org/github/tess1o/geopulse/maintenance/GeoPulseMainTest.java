package org.github.tess1o.geopulse.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GeoPulseMainTest {

    @Test
    @Tag("unit")
    void filtersRuntimeArguments() {
        List<String> args = GeoPulseMain.applicationArgs(
                "-Dquarkus.http.port=0",
                "-XX:MaximumHeapSizePercent=70",
                "admin",
                "reset-password"
        );

        assertEquals(List.of("admin", "reset-password"), args);
    }

    @Test
    @Tag("unit")
    void parsesResetPasswordCommandDefaults() {
        GeoPulseMain.ResetPasswordCommand command = GeoPulseMain.ResetPasswordCommand.parse(
                List.of("--email", "admin@example.com")
        );

        assertEquals("admin@example.com", command.email());
        assertEquals(Optional.empty(), command.password());
        assertFalse(command.promote());
        assertTrue(command.activate());
    }

    @Test
    @Tag("unit")
    void parsesResetPasswordCommandOptions() {
        GeoPulseMain.ResetPasswordCommand command = GeoPulseMain.ResetPasswordCommand.parse(
                List.of("--email", "admin@example.com", "--password", "secret123", "--promote", "--no-activate")
        );

        assertEquals(Optional.of("secret123"), command.password());
        assertTrue(command.promote());
        assertFalse(command.activate());
    }

    @Test
    @Tag("unit")
    void rejectsMissingEmail() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                GeoPulseMain.ResetPasswordCommand.parse(List.of("--promote")));

        assertTrue(exception.getMessage().contains("--email"));
    }
}
