package org.github.tess1o.geopulse.gps.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One GPS point correction inside a bulk edit: the point ID plus the same fields as a single edit.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GpsPointEditItem extends EditGpsPointDto {

    @NotNull(message = "GPS point ID is required")
    @Schema(examples = "101")
    private Long id;

    public GpsPointEditItem(Long id, EditGpsPointDto edit) {
        super(edit.getCoordinates(), edit.getVelocity(), edit.getAccuracy());
        this.id = id;
    }
}
