package org.github.tess1o.geopulse.user.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.user.model.UserAvatarEntity;

import java.util.UUID;

@ApplicationScoped
public class UserAvatarRepository implements PanacheRepositoryBase<UserAvatarEntity, UUID> {
}
