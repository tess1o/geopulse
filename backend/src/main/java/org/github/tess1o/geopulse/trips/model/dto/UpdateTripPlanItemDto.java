package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemTravelMode;

import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTripPlanItemDto {

    @NotBlank(message = "Title cannot be empty")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    @Schema(examples = "Lviv Opera House")
    private String title;

    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    @Schema(examples = "Evening performance at 19:00")
    private String notes;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    @Schema(examples = "49.8441")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    @Schema(examples = "24.0262")
    private Double longitude;

    @Schema(examples = "2025-06-07")
    private LocalDate plannedDay;

    @Schema(examples = "MUST")
    private TripPlanItemPriority priority;

    /** How to get here from the previous stop; null = automatic. */
    @Schema(examples = "WALK")
    private TripPlanItemTravelMode travelMode;

    @Min(value = 0, message = "Order index must be non-negative")
    @Schema(examples = "0")
    private Integer orderIndex;

    // Visit evidence is intentionally absent. It is owned by the auto-matcher and by the
    // visit-override endpoint; including it here let an ordinary edit (renaming a stop)
    // wipe match confidence and clear a user's manual override. Use the override
    // endpoint to change visit state.
}
