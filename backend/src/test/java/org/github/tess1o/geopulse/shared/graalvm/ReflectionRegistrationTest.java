package org.github.tess1o.geopulse.shared.graalvm;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Build-time registration policy, independent of Quarkus's automatic REST registration. */
@Tag("unit")
class ReflectionRegistrationTest {
    static final String APP = "org.github.tess1o.geopulse.";
    static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    static final Set<String> JACKSON_OPERATIONS = Set.of(
            "readValue", "readValues", "writeValue", "writeValueAsString", "writeValueAsBytes",
            "convertValue", "treeToValue", "valueToTree", "readerFor", "readerForListOf",
            "readerForMapOf", "readerForArrayOf", "writerFor", "constructType",
            "constructCollectionType", "constructMapType", "constructParametricType",
            "writeObject", "writePOJO", "readValueAs", "readValuesAs");

    record Root(JavaType type, String path) { }

    @Test
    void allJsonPayloadsAreRegistered() throws Exception {
        List<Class<?>> classes = applicationClasses();
        Set<Class<?>> registered = registrations(classes);
        Map<String, String> reviewed = reviewedBoundaries();
        Set<String> boundaries = jacksonBoundaries(Path.of("src/main/java"));
        List<Root> roots = new ArrayList<>();
        for (Class<?> resource : classes) {
            if (resource.isInterface() || Modifier.isAbstract(resource.getModifiers())) continue;
            for (Method method : resource.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) continue;
                Method declaration = endpointDeclaration(resource, method);
                if (declaration == null) continue;
                String origin = resource.getName() + "#" + method.getName() + "("
                        + String.join(",", Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).toList()) + ")";
                var bindings = MAPPER.constructType(resource).findSuperType(method.getDeclaringClass()).getBindings();
                JavaType response = MAPPER.getTypeFactory().resolveMemberType(method.getGenericReturnType(), bindings);
                if (isOpaque(response)) boundaries.add("REST " + origin + " response");
                else roots.add(new Root(response, origin + " response"));
                Parameter[] parameters = declaration.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (isInjected(parameters[i])) continue;
                    JavaType body = MAPPER.getTypeFactory().resolveMemberType(method.getGenericParameterTypes()[i], bindings);
                    if (isOpaque(body)) boundaries.add("REST " + origin + " request[" + i + "]");
                    else roots.add(new Root(body, origin + " request[" + i + "]"));
                }
            }
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/reflection-boundaries.txt"), boundaries.stream().sorted().toList());
        Set<String> unreviewed = new TreeSet<>(boundaries);
        unreviewed.removeAll(reviewed.keySet());
        Set<String> stale = new TreeSet<>(reviewed.keySet());
        stale.removeAll(boundaries);
        assertThat(unreviewed).as("Unreviewed JSON boundaries; add payload roots or !reason in reflection/boundaries.tsv").isEmpty();
        assertThat(stale).as("Deleted/changed JSON boundaries; remove stale entries from reflection/boundaries.tsv").isEmpty();
        for (var entry : reviewed.entrySet()) {
            if (entry.getValue().startsWith("!")) {
                assertThat(entry.getValue().substring(1).trim()).as("Boundary exclusions need a reason: " + entry.getKey()).isNotEmpty();
            } else {
                for (String name : entry.getValue().split(",")) {
                    roots.add(new Root(MAPPER.constructType(Class.forName(name.trim(), false,
                            getClass().getClassLoader())), entry.getKey()));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Root root : roots) visit(root.type(), root.path(), registered, new HashSet<>(), missing);
        Files.write(Path.of("target/reflection-missing.txt"), missing.stream().distinct().sorted().toList());
        assertThat(missing).as("Register these JSON types in the appropriate *NativeConfig.java (full paths: target/reflection-missing.txt)").isEmpty();
    }

    static boolean isOpaque(JavaType type) {
        Class<?> raw = type.getRawClass();
        if (raw == Object.class || raw == Response.class) return true;
        if (isWrapper(raw)) return type.containedTypeCount() == 0 || isOpaque(type.containedType(0));
        return false;
    }

    static boolean isWrapper(Class<?> type) {
        return type.getName().equals("org.jboss.resteasy.reactive.RestResponse")
                || type.getName().equals("io.smallrye.mutiny.Uni")
                || type.getName().equals("io.smallrye.mutiny.Multi")
                || java.util.concurrent.CompletionStage.class.isAssignableFrom(type)
                || type == Optional.class;
    }

    static boolean isInjected(Parameter parameter) {
        return Arrays.stream(parameter.getAnnotations()).map(Annotation::annotationType).anyMatch(type ->
                type == Context.class || type.getName().startsWith("jakarta.ws.rs.")
                        || type.getName().startsWith("org.jboss.resteasy.reactive.Rest"));
    }

    static Method endpointDeclaration(Class<?> type, Method method) {
        if (type == null) return null;
        try {
            Method declared = type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            if (Arrays.stream(declared.getAnnotations()).anyMatch(a -> a.annotationType().isAnnotationPresent(HttpMethod.class))) {
                return declared;
            }
        } catch (NoSuchMethodException ignored) { /* Endpoint may be inherited. */ }
        for (Class<?> parent : type.getInterfaces()) {
            Method inherited = endpointDeclaration(parent, method);
            if (inherited != null) return inherited;
        }
        return endpointDeclaration(type.getSuperclass(), method);
    }

    static List<Class<?>> applicationClasses() throws Exception {
        Path output = Path.of(ReflectionRegistrationTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolveSibling("classes");
        List<Class<?>> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(output.resolve(APP.replace('.', '/')))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                String name = output.relativize(file).toString().replace('/', '.').replaceAll("\\.class$", "");
                result.add(Class.forName(name, false, ReflectionRegistrationTest.class.getClassLoader()));
            }
        }
        assertThat(result).as("Compiled backend application classes").isNotEmpty();
        return result;
    }

    static Set<Class<?>> registrations(Collection<Class<?>> classes) throws ClassNotFoundException {
        Set<Class<?>> result = new HashSet<>();
        for (Class<?> type : classes) {
            RegisterForReflection annotation = type.getAnnotation(RegisterForReflection.class);
            if (annotation == null) continue;
            Set<Class<?>> targets = new HashSet<>(Arrays.asList(annotation.targets()));
            if (annotation.targets().length == 0 && annotation.classNames().length == 0) targets.add(type);
            for (String name : annotation.classNames()) targets.add(Class.forName(name, false, type.getClassLoader()));
            result.addAll(targets);
            if (!annotation.ignoreNested()) {
                for (Class<?> target : targets) addNested(target, result);
            }
        }
        return result;
    }

    static void addNested(Class<?> type, Set<Class<?>> result) {
        for (Class<?> nested : type.getDeclaredClasses()) {
            result.add(nested);
            addNested(nested, result);
        }
    }

    static void visit(JavaType type, String path, Set<Class<?>> registered, Set<JavaType> seen, List<String> missing) {
        if (type == null || !seen.add(type)) return;
        if (type.isArrayType() || type.isCollectionLikeType() || type.isMapLikeType() || isWrapper(type.getRawClass())) {
            if (type.isArrayType()) visit(type.getContentType(), path + "[]", registered, seen, missing);
            for (int i = 0; i < type.containedTypeCount(); i++) visit(type.containedType(i), path + "<" + i + ">", registered, seen, missing);
            return;
        }
        Class<?> raw = type.getRawClass();
        // shortcut: erased Object payloads rely on reviewed roots; update boundaries.tsv when generic helper callers change.
        if (!raw.getName().startsWith(APP)) return;
        if (Boolean.TRUE.equals(MAPPER.getSerializationConfig().getAnnotationIntrospector()
                .isIgnorableType(MAPPER.getSerializationConfig().introspectClassAnnotations(type).getClassInfo()))) return;
        if (!registered.contains(raw)) missing.add(path + " -> " + raw.getName());
        if (raw.isEnum()) return;
        var serialization = MAPPER.getSerializationConfig().introspect(type);
        var deserialization = MAPPER.getDeserializationConfig().introspect(type);
        var ignored = MAPPER.getSerializationConfig().getAnnotationIntrospector()
                .findPropertyIgnoralByName(MAPPER.getSerializationConfig(), serialization.getClassInfo());
        for (BeanPropertyDefinition property : serialization.findProperties()) {
            if (property.couldSerialize() && (ignored == null || !ignored.findIgnoredForSerialization().contains(property.getName()))) {
                visitProperty(property, path, registered, seen, missing);
            }
        }
        for (BeanPropertyDefinition property : deserialization.findProperties()) {
            if (property.couldDeserialize() && (ignored == null || !ignored.findIgnoredForDeserialization().contains(property.getName()))) {
                visitProperty(property, path, registered, seen, missing);
            }
        }
        JsonSubTypes subtypes = serialization.getClassInfo().getAnnotation(JsonSubTypes.class);
        if (subtypes != null) for (JsonSubTypes.Type subtype : subtypes.value()) {
            visit(MAPPER.constructType(subtype.value()), path + " subtype", registered, seen, missing);
        }
        JsonTypeInfo info = serialization.getClassInfo().getAnnotation(JsonTypeInfo.class);
        if (info != null && info.defaultImpl() != JsonTypeInfo.class && info.defaultImpl() != Void.class) {
            visit(MAPPER.constructType(info.defaultImpl()), path + " default subtype", registered, seen, missing);
        }
    }

    static void visitProperty(BeanPropertyDefinition property, String path, Set<Class<?>> registered,
                              Set<JavaType> seen, List<String> missing) {
        String origin = path + " -> " + property.getName();
        visit(property.getPrimaryType(), origin, registered, seen, missing);
        if (property.getPrimaryMember() != null) {
            var subtypes = MAPPER.getSerializationConfig().getAnnotationIntrospector().findSubtypes(property.getPrimaryMember());
            if (subtypes != null) for (var subtype : subtypes) {
                visit(MAPPER.constructType(subtype.getType()), origin + " subtype", registered, seen, missing);
            }
        }
    }

    static Map<String, String> reviewedBoundaries() throws IOException {
        Map<String, String> result = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/reflection/boundaries.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\t", -1);
            assertThat(parts).as("Boundary must have a key and payload types or !reason: " + line).hasSize(2);
            assertThat(parts[1]).isNotBlank();
            assertThat(result.put(parts[0], parts[1])).as("Duplicate boundary: " + parts[0]).isNull();
        }
        return result;
    }

    static Set<String> jacksonBoundaries(Path source) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            return jacksonBoundaries(files.filter(p -> p.toString().endsWith(".java")).sorted().toList(), source);
        }
    }

    static Set<String> jacksonBoundaries(List<Path> files, Path source) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Run the reflection gate with a JDK, not a JRE").isNotNull();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Set<String> result = new TreeSet<>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : task.parse()) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                        String operation = invocation.getMethodSelect() instanceof MemberSelectTree select
                                ? select.getIdentifier().toString() : invocation.getMethodSelect().toString();
                        recordBoundary(invocation, operation);
                        return super.visitMethodInvocation(invocation, unused);
                    }

                    @Override
                    public Void visitMemberReference(MemberReferenceTree reference, Void unused) {
                        recordBoundary(reference, reference.getName().toString());
                        return super.visitMemberReference(reference, unused);
                    }

                    private void recordBoundary(Tree expression, String operation) {
                        if (!JACKSON_OPERATIONS.contains(operation)) return;
                        String file = source.toAbsolutePath().relativize(Path.of(unit.getSourceFile().toUri())).toString();
                        // Include the expression and enclosing signature: editing a call also requires review.
                        String method = "<initializer>";
                        for (var parent = getCurrentPath(); parent != null; parent = parent.getParentPath()) {
                            if (parent.getLeaf() instanceof MethodTree enclosing) {
                                method = enclosing.getName() + "(" + String.join(",", enclosing.getParameters().stream()
                                        .map(p -> p.getType().toString()).toList()) + ")";
                                break;
                            }
                        }
                        result.add("JACKSON " + file + "#" + method + ": " + expression.toString().replaceAll("\\s+", " "));
                    }
                }.scan(unit, null);
            }
        }
        assertThat(diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList())
                .as("Cannot parse application sources for Jackson boundaries").isEmpty();
        return result;
    }
}
