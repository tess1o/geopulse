package org.github.tess1o.geopulse.shared.graalvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.internal.MessageCatalog;
import org.eclipse.paho.client.mqttv3.internal.NetworkModuleService;
import org.eclipse.paho.client.mqttv3.logging.LoggerFactory;
import org.eclipse.paho.client.mqttv3.spi.NetworkModuleFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MqttServiceLoadingTest {
    @Test
    void applicationDescriptorAndNativeResourcePatternUsePahoSpi() throws Exception {
        String descriptor = "META-INF/services/" + NetworkModuleFactory.class.getName();
        Set<String> schemes = new HashSet<>();
        for (String provider : Files.readAllLines(Path.of("src/main/resources", descriptor))) {
            NetworkModuleFactory factory = Class.forName(provider).asSubclass(NetworkModuleFactory.class)
                    .getConstructor().newInstance();
            schemes.addAll(factory.getSupportedUriSchemes());
        }
        assertThat(schemes).containsExactlyInAnyOrder("tcp", "ssl", "ws", "wss");
        var resources = new ObjectMapper().readTree(Path.of(
                "src/main/resources/META-INF/native-image/resource-config.json").toFile()).get("resources");
        boolean included = false;
        for (var resource : resources) {
            included |= Pattern.matches(resource.get("pattern").asText(), descriptor);
        }
        assertThat(included).as("Native resource config includes the application's Paho SPI descriptor").isTrue();
    }

    @Test
    void loadsEverySupportedTransportWithoutConnecting() {
        NetworkModuleService.validateURI("tcp://localhost:1883");
        NetworkModuleService.validateURI("ssl://localhost:8883");
        NetworkModuleService.validateURI("ws://localhost:8080/mqtt");
        NetworkModuleService.validateURI("wss://localhost:8443/mqtt");
    }

    @Test
    void loadsReflectiveLoggerAndMessageCatalog() {
        assertThat(LoggerFactory.getLogger("org.eclipse.paho.client.mqttv3.internal.nls.logcat", "test"))
                .isNotNull();
        assertThat(MessageCatalog.getMessage(MqttException.REASON_CODE_SERVER_CONNECT_ERROR)).isNotBlank();
    }
}
