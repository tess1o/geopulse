package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The complete, ordered list of a trip's plan items after a drag-and-drop. Each entry's position
 * becomes its order index; {@code plannedDay} is the day it was dropped on (null = unscheduled).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderTripPlanItemsDto {

    @NotNull(message = "Items are required")
    @Valid
    private List<Entry> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        @NotNull(message = "Item id is required")
        @Schema(examples = "31")
        private Long id;

        @Schema(examples = "2025-06-07")
        private LocalDate plannedDay;
    }
}
