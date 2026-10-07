package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPasswordRequest {

    @JsonProperty("password")
    @Schema(examples = "share-password")
    private String password;
}