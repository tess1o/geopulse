package org.github.tess1o.geopulse.notifications.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("unit")
class NotificationPreferencesTest {
    @Test
    void supportsHibernateJsonDeepCopySerialization() {
        assertThatCode(() -> {
            try (var stream = new ObjectOutputStream(new ByteArrayOutputStream())) {
                stream.writeObject(NotificationPreferences.builder().gpsHealth(new NotificationPreferences.Channel()).build());
            }
        }).doesNotThrowAnyException();
    }
}
