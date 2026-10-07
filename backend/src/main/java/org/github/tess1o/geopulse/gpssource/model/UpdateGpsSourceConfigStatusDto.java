package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UpdateGpsSourceConfigStatusDto {
    @Schema(examples = "false")
    private boolean status;
}
