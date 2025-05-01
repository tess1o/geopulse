package org.github.tess1o.geopulse;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.service.UserService;

@Singleton
//TODO delete
public class CreateUsers {
    @Inject
    UserService userService;

    public void loadUsers(@Observes StartupEvent evt) {
        if (userService.getUserById("user").isEmpty()) {
            userService.registerUser("user", "test", "test-device");
        }
    }
}
