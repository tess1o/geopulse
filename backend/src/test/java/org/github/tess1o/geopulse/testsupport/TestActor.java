package org.github.tess1o.geopulse.testsupport;

import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;

public record TestActor(
        UserEntity user,
        UUID userId,
        String email,
        String password,
        String accessToken,
        String refreshToken
) {

    public static TestActor unauthenticated(UserEntity user, String email, String password) {
        return new TestActor(user, user.getId(), email, password, null, null);
    }

    public TestActor withTokens(String accessToken, String refreshToken) {
        return new TestActor(user, userId, email, password, accessToken, refreshToken);
    }
}
