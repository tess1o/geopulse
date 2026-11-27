package org.github.tess1o.geopulse.shared.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;

// Paho reflects only its logger, message catalog and ServiceLoader provider constructors.
@RegisterForReflection(methods = false, fields = false, ignoreNested = true, targets = {
        org.eclipse.paho.client.mqttv3.logging.JSR47Logger.class,
        org.eclipse.paho.client.mqttv3.internal.ResourceBundleCatalog.class,
        org.eclipse.paho.client.mqttv3.internal.TCPNetworkModuleFactory.class,
        org.eclipse.paho.client.mqttv3.internal.SSLNetworkModuleFactory.class,
        org.eclipse.paho.client.mqttv3.internal.websocket.WebSocketNetworkModuleFactory.class,
        org.eclipse.paho.client.mqttv3.internal.websocket.WebSocketSecureNetworkModuleFactory.class
})
public class MqttNativeConfig {
}
