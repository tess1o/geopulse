package org.github.tess1o.geopulse.testsupport;

import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

/**
 * Creates users holding the {@link Role#ADMIN} role.
 *
 * <p>The order in here matters. Roles reach endpoints through the JWT {@code groups} claim, which
 * {@code AuthenticationService.createAccessToken} reads from {@code user.getRole()} at mint time.
 * A token minted before {@code updateRole} therefore still carries {@code USER} and every
 * {@code @RolesAllowed("ADMIN")} endpoint answers 403. So: register, promote, <em>then</em>
 * authenticate.
 *
 * <p>Call within a transaction, as with {@code TestUserFactory}. Admin bootstrap is disabled in
 * tests ({@code geopulse.admin.first-user-admin.enabled=false}), so no user is ever promoted
 * implicitly.
 */
public final class AdminTestUsers {

    private static final String DEFAULT_PASSWORD = "password123";
    private static final String DEFAULT_TIMEZONE = "UTC";

    private AdminTestUsers() {
    }

    /** Registers a user, promotes it to ADMIN, and returns it with a token carrying the ADMIN role. */
    public static TestActor createAdmin(UserService userService,
                                        AuthenticationService authenticationService,
                                        String prefix) {
        UserEntity user = register(userService, prefix);
        UserEntity promoted = userService.updateRole(user.getId(), Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Failed to promote " + user.getId() + " to ADMIN"));
        return withTokens(authenticationService, promoted);
    }

    /** Registers a plain USER-role user, for wrong-role assertions. */
    public static TestActor createRegularUser(UserService userService,
                                              AuthenticationService authenticationService,
                                              String prefix) {
        return withTokens(authenticationService, register(userService, prefix));
    }

    private static UserEntity register(UserService userService, String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        return userService.registerUser(email, DEFAULT_PASSWORD, prefix + " User", DEFAULT_TIMEZONE);
    }

    private static TestActor withTokens(AuthenticationService authenticationService, UserEntity user) {
        AuthResponse authResponse = authenticationService.authenticate(user.getEmail(), DEFAULT_PASSWORD);
        return TestActor.unauthenticated(user, user.getEmail(), DEFAULT_PASSWORD)
                .withTokens(authResponse.getAccessToken(), authResponse.getRefreshToken());
    }
}
