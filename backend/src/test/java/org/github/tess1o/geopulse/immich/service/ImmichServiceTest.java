package org.github.tess1o.geopulse.immich.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.immich.model.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class ImmichServiceTest {

    @Inject
    ImmichService immichService;

    @Inject
    UserRepository userRepository;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        userRepository.findAll().stream().forEach(userRepository::delete);
        
        testUser = UserEntity.builder()
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hashedPassword")
                .isActive(true)
                .immichPreferences(ImmichPreferences.builder()
                        .serverUrl("http://localhost:2283")
                        .apiKey("test-api-key")
                        .enabled(true)
                        .build())
                .build();
        
        userRepository.persist(testUser);
        testUserId = testUser.getId();
    }

    @Test
    void testSearchPhotos_ImmichDisabled() {
        testUser.setImmichPreferences(ImmichPreferences.builder()
                .serverUrl("http://localhost:2283")
                .apiKey("test-api-key")
                .enabled(false)
                .build());
        userRepository.persist(testUser);

        ImmichPhotoSearchRequest searchRequest = new ImmichPhotoSearchRequest();
        searchRequest.setStartDate(OffsetDateTime.now().minusDays(30));
        searchRequest.setEndDate(OffsetDateTime.now());

        var result = immichService.searchPhotos(testUserId, searchRequest);

        var response = result.join();
        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getPhotos().isEmpty());
    }

    @Test
    void testSearchPhotos_UserNotFound() {
        UUID nonExistentUserId = UUID.randomUUID();
        ImmichPhotoSearchRequest searchRequest = new ImmichPhotoSearchRequest();
        searchRequest.setStartDate(OffsetDateTime.now().minusDays(30));
        searchRequest.setEndDate(OffsetDateTime.now());

        assertThrows(IllegalArgumentException.class, () -> {
            immichService.searchPhotos(nonExistentUserId, searchRequest);
        });
    }

    @Test
    void testGetUserImmichConfig() {
        Optional<ImmichConfigResponse> result = immichService.getUserImmichConfig(testUserId);
        
        assertTrue(result.isPresent());
        ImmichConfigResponse config = result.get();
        assertEquals("http://localhost:2283", config.getServerUrl());
        assertTrue(config.getEnabled());
    }

    @Test
    void testGetUserImmichConfig_NotConfigured() {
        testUser.setImmichPreferences(null);
        userRepository.persist(testUser);

        Optional<ImmichConfigResponse> result = immichService.getUserImmichConfig(testUserId);
        
        assertFalse(result.isPresent());
    }

    @Test
    @Transactional
    void testUpdateUserImmichConfig() {
        UpdateImmichConfigRequest request = new UpdateImmichConfigRequest();
        request.setServerUrl("http://new-server:2283");
        request.setApiKey("new-api-key");
        request.setEnabled(true);

        immichService.updateUserImmichConfig(testUserId, request);

        UserEntity updatedUser = userRepository.findById(testUserId);
        assertNotNull(updatedUser.getImmichPreferences());
        assertEquals("http://new-server:2283", updatedUser.getImmichPreferences().getServerUrl());
        assertEquals("new-api-key", updatedUser.getImmichPreferences().getApiKey());
        assertTrue(updatedUser.getImmichPreferences().getEnabled());
    }

    @Test
    void testUpdateUserImmichConfig_NormalizesUrl() {
        UpdateImmichConfigRequest request = new UpdateImmichConfigRequest();
        request.setServerUrl("http://server:2283/");
        request.setApiKey("api-key");
        request.setEnabled(true);

        immichService.updateUserImmichConfig(testUserId, request);

        UserEntity updatedUser = userRepository.findById(testUserId);
        assertEquals("http://server:2283", updatedUser.getImmichPreferences().getServerUrl());
    }

    @Test
    void testUpdateUserImmichConfig_UserNotFound() {
        UUID nonExistentUserId = UUID.randomUUID();
        UpdateImmichConfigRequest request = new UpdateImmichConfigRequest();
        request.setServerUrl("http://server:2283");
        request.setApiKey("api-key");
        request.setEnabled(true);

        assertThrows(IllegalArgumentException.class, () -> {
            immichService.updateUserImmichConfig(nonExistentUserId, request);
        });
    }
}