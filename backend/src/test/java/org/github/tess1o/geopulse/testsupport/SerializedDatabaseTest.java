package org.github.tess1o.geopulse.testsupport;

import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ResourceLock(value = "geopulse-test-db", mode = ResourceAccessMode.READ_WRITE)
public @interface SerializedDatabaseTest {
}
