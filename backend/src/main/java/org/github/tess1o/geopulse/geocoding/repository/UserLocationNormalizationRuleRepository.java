package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;
import org.github.tess1o.geopulse.geocoding.model.UserLocationNormalizationRuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserLocationNormalizationRuleRepository implements PanacheRepository<UserLocationNormalizationRuleEntity> {

    public List<UserLocationNormalizationRuleEntity> findByUserId(UUID userId) {
        return find("user.id = ?1 order by ruleType asc, sourceCountry asc nulls last, sourceCity asc nulls last, id asc", userId)
                .list();
    }

    public Optional<UserLocationNormalizationRuleEntity> findByIdAndUserId(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<UserLocationNormalizationRuleEntity> findCountryRuleBySource(UUID userId, String sourceCountryNorm) {
        return find("user.id = ?1 and ruleType = ?2 and sourceCountryNorm = ?3",
                userId, NormalizationRuleType.COUNTRY, sourceCountryNorm).firstResultOptional();
    }

    public Optional<UserLocationNormalizationRuleEntity> findCityRuleBySource(
            UUID userId,
            String sourceCityNorm) {
        return find("user.id = ?1 and ruleType = ?2 and sourceCityNorm = ?3",
                userId, NormalizationRuleType.CITY, sourceCityNorm).firstResultOptional();
    }
}
