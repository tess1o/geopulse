package org.github.tess1o.geopulse.shared.graalvm;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        DefaultDeserializationContext.class,
        PropertyNamingStrategies.class,
        PropertyNamingStrategies.SnakeCaseStrategy.class,
})
public class JacksonNativeConfig {
}
