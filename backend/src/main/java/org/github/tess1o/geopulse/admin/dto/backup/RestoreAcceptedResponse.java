package org.github.tess1o.geopulse.admin.dto.backup;

import org.github.tess1o.geopulse.admin.backup.RestoreOperationState;

public record RestoreAcceptedResponse(String operationId, RestoreOperationState state) {
}
