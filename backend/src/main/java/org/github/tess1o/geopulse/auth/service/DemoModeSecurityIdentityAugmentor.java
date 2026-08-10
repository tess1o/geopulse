package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;

@ApplicationScoped
public class DemoModeSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    @Inject
    DemoModeService demoModeService;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (!demoModeService.isDemoRestricted(identity)) {
            return Uni.createFrom().item(identity);
        }

        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity)
                .addRole(SecurityRoles.DEMO_USER);

        if (demoModeService.isAdminReadOnlyEnabled()) {
            builder.addRole(SecurityRoles.DEMO_ADMIN_READ);
        }

        return Uni.createFrom().item(builder.build());
    }
}
