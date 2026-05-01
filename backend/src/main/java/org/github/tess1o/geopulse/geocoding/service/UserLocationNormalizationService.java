package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.github.tess1o.geopulse.geocoding.dto.CreateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.dto.NormalizationRuleDto;
import org.github.tess1o.geopulse.geocoding.dto.UpdateNormalizationRuleRequest;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;
import org.github.tess1o.geopulse.geocoding.model.UserLocationNormalizationRuleEntity;
import org.github.tess1o.geopulse.geocoding.repository.UserLocationNormalizationRuleRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserLocationNormalizationService {

    private final UserLocationNormalizationRuleRepository repository;
    private final EntityManager entityManager;

    @Inject
    public UserLocationNormalizationService(UserLocationNormalizationRuleRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public List<NormalizationRuleDto> listRules(UUID userId) {
        return repository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    public NormalizationRuleDto getRule(UUID userId, Long ruleId) {
        UserLocationNormalizationRuleEntity entity = repository.findByIdOptional(ruleId)
                .orElseThrow(() -> new NotFoundException("Normalization rule not found: " + ruleId));
        if (!entity.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Cannot access another user's normalization rule");
        }
        return toDto(entity);
    }

    @Transactional
    public NormalizationRuleDto createRule(UUID userId, CreateNormalizationRuleRequest request) {
        UserLocationNormalizationRuleEntity entity = UserLocationNormalizationRuleEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .ruleType(request.getRuleType())
                .sourceCountry(clean(request.getSourceCountry()))
                .sourceCity(clean(request.getSourceCity()))
                .targetCountry(clean(request.getTargetCountry()))
                .targetCity(clean(request.getTargetCity()))
                .build();
        sanitizeFieldsForRuleType(entity);
        applyNormalizedKeys(entity);
        ensureNoConflict(userId, entity, null);
        repository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public NormalizationRuleDto updateRule(UUID userId, Long ruleId, UpdateNormalizationRuleRequest request) {
        UserLocationNormalizationRuleEntity entity = repository.findByIdOptional(ruleId)
                .orElseThrow(() -> new NotFoundException("Normalization rule not found: " + ruleId));
        if (!entity.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Cannot update another user's normalization rule");
        }

        entity.setRuleType(request.getRuleType());
        entity.setSourceCountry(clean(request.getSourceCountry()));
        entity.setSourceCity(clean(request.getSourceCity()));
        entity.setTargetCountry(clean(request.getTargetCountry()));
        entity.setTargetCity(clean(request.getTargetCity()));
        sanitizeFieldsForRuleType(entity);
        applyNormalizedKeys(entity);
        ensureNoConflict(userId, entity, ruleId);
        repository.persist(entity);
        return toDto(entity);
    }

    @Transactional
    public void deleteRule(UUID userId, Long ruleId) {
        UserLocationNormalizationRuleEntity entity = repository.findByIdOptional(ruleId)
                .orElseThrow(() -> new NotFoundException("Normalization rule not found: " + ruleId));
        if (!entity.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Cannot delete another user's normalization rule");
        }
        repository.delete(entity);
    }

    public NormalizedLocation normalizeForUser(UUID userId, String city, String country) {
        String normalizedCityValue = clean(city);
        String normalizedCountryValue = clean(country);

        List<UserLocationNormalizationRuleEntity> rules = repository.findByUserId(userId);
        if (rules.isEmpty()) {
            return new NormalizedLocation(normalizedCityValue, normalizedCountryValue, false);
        }

        String sourceCityNorm = normalizeKey(normalizedCityValue);
        String sourceCountryNorm = normalizeKey(normalizedCountryValue);

        // City rule wins first (city-only mapping, country unchanged).
        if (sourceCityNorm != null) {
            Optional<UserLocationNormalizationRuleEntity> cityRule = Optional.empty();
            for (UserLocationNormalizationRuleEntity rule : rules) {
                if (rule.getRuleType() == NormalizationRuleType.CITY
                        && sourceCityNorm.equals(rule.getSourceCityNorm())) {
                    cityRule = Optional.of(rule);
                    break;
                }
            }
            if (cityRule.isPresent()) {
                UserLocationNormalizationRuleEntity rule = cityRule.get();
                normalizedCityValue = clean(rule.getTargetCity());
            }
        }

        // Then apply country-level mapping.
        if (sourceCountryNorm != null) {
            Optional<UserLocationNormalizationRuleEntity> countryRule = Optional.empty();
            for (UserLocationNormalizationRuleEntity rule : rules) {
                if (rule.getRuleType() == NormalizationRuleType.COUNTRY
                        && sourceCountryNorm.equals(rule.getSourceCountryNorm())) {
                    countryRule = Optional.of(rule);
                    break;
                }
            }
            if (countryRule.isPresent()) {
                normalizedCountryValue = clean(countryRule.get().getTargetCountry());
            }
        }

        boolean changed = !safeEquals(clean(city), normalizedCityValue)
                || !safeEquals(clean(country), normalizedCountryValue);
        return new NormalizedLocation(normalizedCityValue, normalizedCountryValue, changed);
    }

    public String normalizeCountryName(String countryName) {
        return clean(countryName);
    }

    public NormalizedLocation applySingleRule(NormalizationRuleDto rule, String city, String country) {
        String normalizedCityValue = clean(city);
        String normalizedCountryValue = clean(country);

        if (rule == null || rule.getRuleType() == null) {
            return new NormalizedLocation(normalizedCityValue, normalizedCountryValue, false);
        }

        if (rule.getRuleType() == NormalizationRuleType.CITY) {
            String sourceCityNorm = normalizeKey(normalizedCityValue);
            String ruleSourceCityNorm = normalizeKey(rule.getSourceCity());
            if (sourceCityNorm != null && sourceCityNorm.equals(ruleSourceCityNorm)) {
                normalizedCityValue = clean(rule.getTargetCity());
            }
        } else if (rule.getRuleType() == NormalizationRuleType.COUNTRY) {
            String sourceCountryNorm = normalizeKey(normalizedCountryValue);
            String ruleSourceCountryNorm = normalizeKey(rule.getSourceCountry());
            if (sourceCountryNorm != null && sourceCountryNorm.equals(ruleSourceCountryNorm)) {
                normalizedCountryValue = clean(rule.getTargetCountry());
            }
        }

        boolean changed = !safeEquals(clean(city), normalizedCityValue)
                || !safeEquals(clean(country), normalizedCountryValue);
        return new NormalizedLocation(normalizedCityValue, normalizedCountryValue, changed);
    }

    private void ensureNoConflict(UUID userId, UserLocationNormalizationRuleEntity candidate, Long currentRuleId) {
        if (candidate.getRuleType() == NormalizationRuleType.COUNTRY) {
            Optional<UserLocationNormalizationRuleEntity> conflict =
                    repository.findCountryRuleBySource(userId, candidate.getSourceCountryNorm());
            if (conflict.isPresent() && !conflict.get().getId().equals(currentRuleId)) {
                throw new IllegalArgumentException("Country mapping for this source already exists");
            }
            return;
        }

        Optional<UserLocationNormalizationRuleEntity> conflict =
                repository.findCityRuleBySource(userId, candidate.getSourceCityNorm());
        if (conflict.isPresent() && !conflict.get().getId().equals(currentRuleId)) {
            throw new IllegalArgumentException("City mapping for this source city already exists");
        }
    }

    private void sanitizeFieldsForRuleType(UserLocationNormalizationRuleEntity entity) {
        if (entity.getRuleType() == NormalizationRuleType.CITY) {
            entity.setSourceCountry(null);
            entity.setTargetCountry(null);
            return;
        }

        if (entity.getRuleType() == NormalizationRuleType.COUNTRY) {
            entity.setSourceCity(null);
            entity.setTargetCity(null);
        }
    }

    private void applyNormalizedKeys(UserLocationNormalizationRuleEntity entity) {
        entity.setSourceCountryNorm(normalizeKey(entity.getSourceCountry()));
        entity.setSourceCityNorm(normalizeKey(entity.getSourceCity()));
    }

    private NormalizationRuleDto toDto(UserLocationNormalizationRuleEntity entity) {
        return NormalizationRuleDto.builder()
                .id(entity.getId())
                .ruleType(entity.getRuleType())
                .sourceCountry(entity.getSourceCountry())
                .sourceCity(entity.getSourceCity())
                .targetCountry(entity.getTargetCountry())
                .targetCity(entity.getTargetCity())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeKey(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        return Normalizer.normalize(cleaned, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    public record NormalizedLocation(
            String city,
            String country,
            boolean changed
    ) {}
}
