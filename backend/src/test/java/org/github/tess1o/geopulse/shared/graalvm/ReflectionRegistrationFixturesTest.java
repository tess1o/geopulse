package org.github.tess1o.geopulse.shared.graalvm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.GET;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.*;
import static org.github.tess1o.geopulse.shared.graalvm.ReflectionRegistrationTest.*;

@Tag("unit")
class ReflectionRegistrationFixturesTest {
    static class Parent<T> { public T inherited; }
    static class Payload extends Parent<Child> {
        public List<Child> children;
        public Map<Key, Child[]> mapping;
        public Payload cycle;
        @JsonIgnore public Ignored ignored;
    }
    static class Child { public String value; }
    static class InheritedOnly extends Parent<Child> { }
    record Box<T>(T value) { }
    static class Envelope { public Box<Child> box; }
    static class PropertySubtype {
        @JsonSubTypes(@JsonSubTypes.Type(Child.class)) public Object value;
    }
    static class Ignored { public String secret; }
    @JsonIgnoreProperties("ignored")
    static class ClassIgnored { public Ignored ignored; }
    enum Key { ONE }
    @JsonSubTypes(@JsonSubTypes.Type(Child.class))
    interface Polymorphic { }
    @RegisterForReflection(ignoreNested = true)
    static class Direct { static class Unrelated { } }
    @RegisterForReflection(targets = Payload.class, ignoreNested = true)
    static class NativeConfig { }
    interface Endpoint<T> { @GET CompletionStage<List<T>> get(); }
    static class Resource implements Endpoint<Payload> {
        public CompletionStage<List<Payload>> get() { return null; }
    }

    @Test
    void followsNestedGenericInheritedPropertiesAndCyclesButIgnoresIgnoredProperties() {
        List<String> missing = new ArrayList<>();
        visit(MAPPER.constructType(Payload.class), "response", Set.of(), new HashSet<>(), missing);
        assertThat(missing).anyMatch(s -> s.endsWith(Payload.class.getName()))
                .anyMatch(s -> s.endsWith(Child.class.getName()))
                .anyMatch(s -> s.endsWith(Key.class.getName()))
                .noneMatch(s -> s.endsWith(Ignored.class.getName()));
        missing.clear();
        visit(MAPPER.constructType(Payload.class), "response", Set.of(Payload.class, Child.class, Key.class), new HashSet<>(), missing);
        assertThat(missing).isEmpty();
    }

    @Test
    void resolvesGenericArgumentsInPropertiesAndSuperclasses() {
        for (Class<?> root : List.of(InheritedOnly.class, Envelope.class, PropertySubtype.class)) {
            List<String> missing = new ArrayList<>();
            visit(MAPPER.constructType(root), "response", Set.of(root, Box.class), new HashSet<>(), missing);
            assertThat(missing).anyMatch(s -> s.endsWith(Child.class.getName()));
        }
    }

    @Test
    void explicitMapperRootsRequireRegistrationIncludingNestedTypes() {
        List<String> missing = new ArrayList<>();
        visit(MAPPER.constructType(Envelope.class), "mapper payload", Set.of(Envelope.class, Box.class), new HashSet<>(), missing);
        assertThat(missing).singleElement().asString().contains("mapper payload -> box -> value").endsWith(Child.class.getName());
    }

    @Test
    void honorsClassLevelIgnoredProperties() {
        List<String> missing = new ArrayList<>();
        visit(MAPPER.constructType(ClassIgnored.class), "response", Set.of(ClassIgnored.class), new HashSet<>(), missing);
        assertThat(missing).isEmpty();
    }

    @Test
    void recognizesDirectAndTargetRegistrationsWithoutRequiringUnrelatedNestedTypes() throws Exception {
        assertThat(registrations(List.of(Direct.class, NativeConfig.class)))
                .contains(Direct.class, Payload.class).doesNotContain(Direct.Unrelated.class);
    }

    @Test
    void discoversInheritedRestDeclarationsAndUnwrapsGenericResponses() throws Exception {
        var method = Resource.class.getMethod("get");
        assertThat(endpointDeclaration(Resource.class, method).getDeclaringClass()).isEqualTo(Endpoint.class);
        List<String> missing = new ArrayList<>();
        visit(MAPPER.constructType(method.getGenericReturnType()), "response", Set.of(), new HashSet<>(), missing);
        assertThat(missing).anyMatch(s -> s.endsWith(Payload.class.getName()));
        assertThat(isOpaque(MAPPER.constructType(jakarta.ws.rs.core.Response.class))).isTrue();
    }

    @Test
    void followsDeclaredPolymorphicSubtypes() {
        List<String> missing = new ArrayList<>();
        visit(MAPPER.constructType(Polymorphic.class), "response", Set.of(Polymorphic.class), new HashSet<>(), missing);
        assertThat(missing).anyMatch(s -> s.endsWith(Child.class.getName()));
    }

    @Test
    void newOrChangedMapperBoundariesRequireReviewAndInvalidSourcesFail(@TempDir Path source) throws Exception {
        Path file = source.resolve("Example.java");
        Files.writeString(file, "class Example { void save() { mapper.writeValueAsString(payload); } }");
        Set<String> initial = jacksonBoundaries(source);
        assertThat(initial).hasSize(1).anyMatch(s -> s.contains("writeValueAsString(payload)"));
        Files.writeString(file, "class Example { void save() { mapper.writeValueAsString(other); mapper.readValue(json, Payload.class); } }");
        assertThat(jacksonBoundaries(source)).hasSize(2).doesNotContainAnyElementsOf(initial);
        Files.writeString(file, "class Example { void save() { Object callback = mapper::writeValueAsString; } }");
        assertThat(jacksonBoundaries(source)).singleElement().asString().contains("mapper::writeValueAsString");
        Files.writeString(file, "class Example { void save( {");
        assertThatThrownBy(() -> jacksonBoundaries(source)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Cannot parse application sources");
    }
}
