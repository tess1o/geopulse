package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTripPlanItemDto {

    @NotBlank(message = "Title cannot be empty")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    private String notes;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private Double longitude;

    private LocalDate plannedDay;

    private TripPlanItemPriority priority;

    @Min(value = 0, message = "Order index must be non-negative")
    private Integer orderIndex;

    // Visit evidence is intentionally absent. It is owned by the auto-matcher and by the
    // visit-override endpoint; including it here let an ordinary edit (renaming a stop)
    // wipe match confidence and clear a user's manual override. Use the override
    // endpoint to change visit state.
}
