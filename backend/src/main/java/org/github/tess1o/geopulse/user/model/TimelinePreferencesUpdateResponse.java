package org.github.tess1o.geopulse.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStatusDTO;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelinePreferencesUpdateResponse(
        UUID jobId,
        UUID boatSetupJobId,
        BoatSetupStatusDTO boatSetupStatus) {
}
