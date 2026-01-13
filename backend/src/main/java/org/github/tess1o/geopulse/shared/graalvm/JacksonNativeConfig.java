package org.github.tess1o.geopulse.shared.graalvm;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        DefaultDeserializationContext.class,
        PropertyNamingStrategies.class,
        PropertyNamingStrategies.SnakeCaseStrategy.class,
        // Java 8 Time Support
        JavaTimeModule.class,
        LocalDateDeserializer.class,
        LocalDateTimeDeserializer.class,
        InstantDeserializer.class,
        LocalDateSerializer.class,
        LocalDateTimeSerializer.class,
        InstantSerializer.class,
        StdDeserializer.class,
        StdSerializer.class,
        // Java time classes themselves
        java.time.LocalDate.class,
        java.time.LocalDateTime.class,
        java.time.Instant.class,
})
public class JacksonNativeConfig {
}
