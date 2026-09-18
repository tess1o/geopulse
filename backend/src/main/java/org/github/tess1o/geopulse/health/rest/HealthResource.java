package org.github.tess1o.geopulse.health.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.SERVICE_UNAVAILABLE;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/system/health")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "User: System", description = "Check service health.")
public class HealthResource {

    private final EntityManager entityManager;

    @Inject
    public HealthResource(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GET
    public HealthStatusResponse checkHealth() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return new HealthStatusResponse(HealthStatus.UP, HealthStatus.UP);
        } catch (Exception e) {
            log.error("Health check failed", e);
            throw problem(SERVICE_UNAVAILABLE, "Database health check failed",
                    Map.of("database", HealthStatus.DOWN.name()));
        }
    }

    public record HealthStatusResponse(HealthStatus status, HealthStatus database) { }

    public enum HealthStatus {
        UP,
        DOWN
    }
}
