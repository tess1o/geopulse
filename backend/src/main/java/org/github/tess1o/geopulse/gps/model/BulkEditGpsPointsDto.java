package org.github.tess1o.geopulse.gps.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for applying several GPS point corrections at once.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkEditGpsPointsDto {

    public static final int MAX_EDITS = 1000;

    @NotEmpty(message = "Edits list cannot be empty")
    @Size(max = MAX_EDITS, message = "At most " + MAX_EDITS + " GPS points can be edited at once")
    @Valid
    private List<GpsPointEditItem> edits;
}
