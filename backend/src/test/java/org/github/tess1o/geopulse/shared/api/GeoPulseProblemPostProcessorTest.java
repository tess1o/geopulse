package org.github.tess1o.geopulse.shared.api;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers what the post-processor is responsible for on its own: adding the wire fields, and recovering an
 * application exception that never reached its own mapper — including when its class comes from a previous
 * application's classloader.
 */
@Tag("unit")
class GeoPulseProblemPostProcessorTest {

    private final GeoPulseProblemPostProcessor processor = new GeoPulseProblemPostProcessor();

    @BeforeEach
    void setUp() {
        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.get(RequestCorrelationHandler.REQUEST_ID)).thenReturn("request-1");
        processor.routingContext = routingContext;
    }

    @Test
    void alreadyCodedProblemIsNotReDerivedFromTheCause() {
        HttpProblem classified = ProblemFactory.from(
                new GeoPulseException(ApiErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        HttpProblem result = processor.apply(classified, ProblemContext.of(
                new GeoPulseException(ApiErrorCode.NOT_FOUND, "Trip not found"), "/api/v1/auth/sessions"));

        assertThat(result.getStatusCode()).isEqualTo(401);
        assertThat(result.getDetail()).isEqualTo("Invalid email or password");
        assertThat(result.getParameters().get("code")).isEqualTo(ApiErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void wrappedApplicationExceptionIsRecoveredWhenNothingClassifiedTheProblem() {
        GeoPulseException application = new GeoPulseException(ApiErrorCode.TRIP_NOT_FOUND, "Trip not found");
        RuntimeException wrapper = new RuntimeException("downstream failure", application);
        HttpProblem unclassified = HttpProblem.builder()
                .withStatus(500)
                .withTitle("Internal Server Error")
                .build();

        HttpProblem result = processor.apply(unclassified, ProblemContext.of(wrapper, "/api/v1/trips/1"));

        assertThat(result.getStatusCode()).isEqualTo(404);
        assertThat(result.getDetail()).isEqualTo("Trip not found");
        assertThat(result.getParameters().get("code")).isEqualTo(ApiErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    void everyProblemGetsTheWireFields() {
        HttpProblem frameworkProblem = HttpProblem.builder()
                .withStatus(404)
                .withTitle("Not Found")
                .build();

        // ProblemContext.path comes from UriInfo.getPath(), which has no leading slash.
        HttpProblem result = processor.apply(frameworkProblem,
                ProblemContext.of(new IllegalStateException("no route"), "api/v1/nope"));

        assertThat(result.getParameters().get("code")).isEqualTo(ApiErrorCode.NOT_FOUND);
        assertThat(result.getType()).hasToString("urn:geopulse:error:NOT_FOUND");
        // A readable absolute path, not the library's percent-encoded "api%2Fv1%2Fnope".
        assertThat(result.getInstance()).hasToString("/api/v1/nope");
        assertThat(result.getParameters().get("requestId")).isEqualTo("request-1");
        assertThat(result.getParameters().get("errorId")).isNotNull();
        assertThat(result.getHeaders())
                .containsKey(RequestCorrelationHandler.ERROR_ID_HEADER);
        assertThat(result.getHeaders().get(RequestCorrelationHandler.ERROR_ID_HEADER))
                .isEqualTo(result.getParameters().get("errorId"));
    }

    @Test
    void existingErrorIdAndHeadersSurviveNormalization() {
        HttpProblem classified = ProblemFactory.from(new GeoPulseException(ApiErrorCode.ACCESS_DENIED, "Nope"));
        String originalErrorId = classified.getParameters().get("errorId").toString();
        HttpProblem withHeader = HttpProblem.builder(classified)
                .withHeader("X-GeoPulse-Demo-Blocked", "true")
                .build();

        HttpProblem result = processor.apply(withHeader, ProblemContext.of(
                new GeoPulseException(ApiErrorCode.ACCESS_DENIED, "Nope"), "/api/v1/friends"));

        assertThat(result.getHeaders()).containsEntry("X-GeoPulse-Demo-Blocked", "true");
        assertThat(result.getParameters().get("errorId")).isEqualTo(originalErrorId);
    }

    /**
     * An application restart in one JVM (tests, continuous testing) leaves the previous application's
     * post-processor registered, and the values it reads back are instances of the previous application's
     * {@code ApiErrorCode} — a class this application cannot cast to. Resolving by name keeps the code.
     */
    @Test
    void codeFromAPreviousApplicationClassloaderIsResolvedByName() throws Exception {
        Object foreignTripNotFound = foreignConstant("TRIP_NOT_FOUND");

        HttpProblem classified = HttpProblem.builder()
                .withStatus(404)
                .withTitle("Not Found")
                .withDetail("Trip not found")
                .with("code", foreignTripNotFound)
                .build();

        HttpProblem result = processor.apply(classified, ProblemContext.of(
                new RuntimeException("no route"), "/api/v1/trips/1"));

        assertThat(result.getParameters().get("code")).isEqualTo(ApiErrorCode.TRIP_NOT_FOUND);
        assertThat(result.getType()).hasToString("urn:geopulse:error:TRIP_NOT_FOUND");
    }

    /** The same restart, on the path where the exception itself never reached its own mapper. */
    @Test
    void applicationExceptionFromAPreviousApplicationClassloaderIsRecovered() throws Exception {
        ClassLoader foreignLoader = foreignClassLoader();
        Class<?> foreignApiErrorCode = foreignLoader.loadClass(ApiErrorCode.class.getName());
        Class<?> foreignException = foreignLoader.loadClass(GeoPulseException.class.getName());
        assertThat(foreignException).isNotSameAs(GeoPulseException.class);

        Object foreignTripNotFound = foreignApiErrorCode.getField("TRIP_NOT_FOUND").get(null);
        Throwable application = (Throwable) foreignException
                .getConstructor(foreignApiErrorCode, String.class)
                .newInstance(foreignTripNotFound, "Trip not found");
        String originalErrorId = (String) foreignException.getMethod("errorId").invoke(application);
        HttpProblem unclassified = HttpProblem.builder()
                .withStatus(500)
                .withTitle("Internal Server Error")
                .build();

        HttpProblem result = processor.apply(unclassified, ProblemContext.of(
                new RuntimeException("downstream failure", application), "/api/v1/trips/1"));

        assertThat(result.getStatusCode()).isEqualTo(404);
        assertThat(result.getDetail()).isEqualTo("Trip not found");
        assertThat(result.getParameters().get("code")).isEqualTo(ApiErrorCode.TRIP_NOT_FOUND);
        // The id the exception was created with, not a freshly generated one.
        assertThat(result.getParameters().get("errorId")).isEqualTo(originalErrorId);
    }

    /**
     * The application's own classes, loaded a second time by a classloader that does not delegate to this one —
     * the closest a unit test gets to the application instance a restart produces.
     */
    private static ClassLoader foreignClassLoader() throws Exception {
        URL classesDirectory = ApiErrorCode.class.getProtectionDomain().getCodeSource().getLocation();
        return new URLClassLoader(new URL[] {classesDirectory}, null);
    }

    private static Object foreignConstant(String name) throws Exception {
        Class<?> foreignApiErrorCode = foreignClassLoader().loadClass(ApiErrorCode.class.getName());
        assertThat(foreignApiErrorCode).isNotSameAs(ApiErrorCode.class);
        return foreignApiErrorCode.getField(name).get(null);
    }
}
