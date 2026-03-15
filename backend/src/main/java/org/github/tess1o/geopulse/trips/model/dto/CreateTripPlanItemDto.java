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
public class CreateTripPlanItemDto {

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
}
