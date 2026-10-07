package org.github.tess1o.geopulse.digest.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.common.annotation.Blocking;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.digest.service.DigestPdfService;
import org.github.tess1o.geopulse.digest.service.DigestService;

import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/digests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
@Tag(name = ApiTags.DIGESTS)
public class DigestResource {

    @Inject
    DigestService digestService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    DigestPdfService digestPdfService;

    @GET
    @Path("/monthly")
    @APIResponse(responseCode = "200", description = "Monthly digest retrieved")
    @APIResponse(responseCode = "400", description = "Invalid digest period")
    @Operation(summary = "Get a monthly digest",
            description = "Returns the digest of a calendar month in the user's timezone: key metrics such as "
                    + "distance and time, top places, highlights, milestones, activity charts, and a comparison "
                    + "with the previous month.")
    public TimeDigest getMonthlyDigest(
            @Parameter(description = "Year, from 2000 to 2100.", example = "2025")
            @QueryParam("year") int year,
            @Parameter(description = "Month, from 1 to 12.", example = "6")
            @QueryParam("month") int month) {
        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        // Validate parameters
        if (year < 2000 || year > 2100) {
            throw new GeoPulseException(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }

        if (month < 1 || month > 12) {
            throw new GeoPulseException(INVALID_DIGEST_MONTH, "Invalid month. Must be between 1 and 12",
                    Map.of("month", month, "min", 1, "max", 12));
        }

        log.info("Received request for monthly digest: user={}, year={}, month={}", userId, year, month);

        return digestService.getMonthlyDigest(userId, year, month, timezone);
    }

    @GET
    @Path("/yearly")
    @APIResponse(responseCode = "200", description = "Yearly digest retrieved")
    @APIResponse(responseCode = "400", description = "Invalid digest year")
    @Operation(summary = "Get a yearly digest",
            description = "Returns the digest of a calendar year in the user's timezone, with the same sections as "
                    + "the monthly digest and a comparison with the previous year.")
    public TimeDigest getYearlyDigest(
            @Parameter(description = "Year, from 2000 to 2100.", example = "2025")
            @QueryParam("year") int year) {
        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        // Validate parameters
        if (year < 2000 || year > 2100) {
            throw new GeoPulseException(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }

        log.info("Received request for yearly digest: user={}, year={}", userId, year);

        return digestService.getYearlyDigest(userId, year, timezone);
    }

    @GET
    @Path("/pdf")
    @Produces({"application/pdf", "application/problem+json"})
    @Blocking
    @APIResponse(
            responseCode = "200",
            description = "Generated Rewind PDF",
            content = @Content(
                    mediaType = "application/pdf",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")
            )
    )
    @APIResponse(
            responseCode = "400",
            description = "Invalid export parameters"
    )
    @Operation(summary = "Download a digest as PDF",
            description = "Generates the monthly or yearly digest (\"Rewind\") as a PDF file, optionally with Immich "
                    + "photos.")
    public Response exportPdf(
            @Parameter(description = "`monthly` or `yearly`.", example = "monthly")
            @QueryParam("viewMode") String viewMode,
            @Parameter(description = "Year, from 2000 to 2100.", example = "2025")
            @QueryParam("year") int year,
            @Parameter(description = "Month, from 1 to 12. Required when `viewMode` is `monthly`.", example = "6")
            @QueryParam("month") Integer month,
            @DefaultValue("false")
            @Parameter(description = "Include photos from Immich. Defaults to `false`.")
            @QueryParam("includePhotos") boolean includePhotos) {
        if (!"monthly".equals(viewMode) && !"yearly".equals(viewMode)) {
            throw new GeoPulseException(INVALID_DIGEST_VIEW_MODE, "viewMode must be monthly or yearly",
                    Map.of("viewMode", String.valueOf(viewMode)));
        }
        if (year < 2000 || year > 2100) {
            throw new GeoPulseException(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }
        if ("monthly".equals(viewMode) && (month == null || month < 1 || month > 12)) {
            throw new GeoPulseException(INVALID_DIGEST_MONTH, "A month between 1 and 12 is required for monthly exports",
                    Map.of("month", month == null ? "" : month, "min", 1, "max", 12));
        }

        var user = currentUserService.getCurrentUser();
        TimeDigest digest = "monthly".equals(viewMode)
                ? digestService.getMonthlyDigest(user.getId(), year, month, user.getTimezone())
                : digestService.getYearlyDigest(user.getId(), year, user.getTimezone());
        String suffix = "monthly".equals(viewMode) ? "%d-%02d".formatted(year, month) : String.valueOf(year);
        byte[] report = digestPdfService.generate(digest, user, includePhotos);
        return Response.ok(report, "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"geopulse-rewind-" + suffix + ".pdf\"")
                .build();
    }
}
