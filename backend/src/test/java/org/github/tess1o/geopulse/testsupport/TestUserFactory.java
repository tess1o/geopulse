package org.github.tess1o.geopulse.testsupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

@ApplicationScoped
public class TestUserFactory {
    private static final String DEFAULT_PASSWORD = "password123";
    private static final String DEFAULT_TIMEZONE = "UTC";
    @Inject
    UserService userService;
    @Inject
    AuthenticationService authenticationService;
    public TestActor createUser(String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        String fullName = prefix + " User";
        UserEntity user = userService.registerUser(email, DEFAULT_PASSWORD, fullName, DEFAULT_TIMEZONE);
        return TestActor.unauthenticated(user, email, DEFAULT_PASSWORD);
    }
    public TestActor createAuthenticatedUser(String prefix) {
        TestActor actor = createUser(prefix);
        AuthResponse authResponse = authenticationService.authenticate(actor.email(), actor.password());
        return actor.withTokens(authResponse.getAccessToken(), authResponse.getRefreshToken());
    }
    public TestActorPair createUserPair(String prefixA, String prefixB) {
        return new TestActorPair(createUser(prefixA), createUser(prefixB));
    }
    public record TestActorPair(TestActor first, TestActor second) {
    }
}
