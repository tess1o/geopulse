package org.github.tess1o.geopulse.admin.service;

import java.util.UUID;

public record AdminPasswordRecoveryResult(
        UUID userId,
        String email,
        boolean generatedPassword,
        String temporaryPassword,
        boolean promoted,
        boolean activated
) {
}
