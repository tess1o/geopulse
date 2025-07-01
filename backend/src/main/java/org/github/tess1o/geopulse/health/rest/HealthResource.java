package org.github.tess1o.geopulse.health.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.HashMap;
import java.util.Map;

@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class HealthResource {

    private final EntityManager entityManager;

    @Inject
    public HealthResource(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GET
    public Response checkHealth() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            
            Map<String, Object> healthStatus = new HashMap<>();
            healthStatus.put("status", "UP");
            healthStatus.put("database", "UP");
            
            return Response.ok(ApiResponse.success(healthStatus)).build();
        } catch (Exception e) {
            log.error("Health check failed", e);
            
            Map<String, Object> healthStatus = new HashMap<>();
            healthStatus.put("status", "DOWN");
            healthStatus.put("database", "DOWN");
            healthStatus.put("error", e.getMessage());
            
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiResponse.error("Health check failed", healthStatus))
                    .build();
        }
    }
}