package org.github.tess1o.geopulse.favorites.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@ApplicationScoped
public class FavoritesRepository implements PanacheRepository<FavoritesEntity> {

    private final EntityManager em;

    @Inject
    public FavoritesRepository(EntityManager em) {
        this.em = em;
    }

    public List<FavoritesEntity> findByUserId(UUID userId) {
        return list("user.id", userId);
    }

    public Optional<FavoritesEntity> findByPoint(UUID userId, Point point, int maxDistanceFromPoint, int maxDistanceFromArea) {
        String query = """
                SELECT *
                FROM favorite_locations
                WHERE user_id = :userId AND ((
                    type = 'POINT'
                    AND ST_DWithin(
                        geometry::geography,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                        :maxDistanceFromPoint
                    )
                )
                OR (
                    type = 'AREA'
                    AND (
                        ST_Contains(geometry, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                        OR ST_DWithin(
                            ST_Boundary(geometry)::geography,
                            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                            :maxDistanceFromArea
                        )
                    )
                ));
                """;

        var resultList = em.createNativeQuery(query, FavoritesEntity.class)
                .setParameter("lon", point.getX())
                .setParameter("lat", point.getY())
                .setParameter("userId", userId)
                .setParameter("maxDistanceFromPoint", maxDistanceFromPoint)
                .setParameter("maxDistanceFromArea", maxDistanceFromArea)
                .getResultList();

        if (!resultList.isEmpty()) {
            return Optional.of((FavoritesEntity) resultList.getFirst());
        }
        return Optional.empty();
    }

    public boolean existsByUserAndName(UUID userId, String name) {
        return count("user.id = ?1 AND name = ?2", userId, name) > 0;
    }

    public long deleteByUserId(UUID userId) {
        return delete("user.id = ?1", userId);
    }

}
