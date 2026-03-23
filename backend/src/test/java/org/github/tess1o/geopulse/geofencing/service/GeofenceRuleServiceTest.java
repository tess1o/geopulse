package org.github.tess1o.geopulse.geofencing.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceRuleDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectId;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeofenceRuleServiceTest {

    @Mock
    GeofenceRuleRepository ruleRepository;

    @Mock
    NotificationTemplateRepository templateRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    FriendshipRepository friendshipRepository;

    @Mock
    UserFriendPermissionRepository permissionRepository;

    @Mock
    EntityManager entityManager;

    private GeofenceRuleService service;

    @BeforeEach
    void setUp() {
        service = new GeofenceRuleService(
                ruleRepository,
                templateRepository,
                userRepository,
                friendshipRepository,
                permissionRepository,
                entityManager
        );
    }

    @Test
    void shouldRejectFriendSubjectWithoutLiveLocationPermission() {
        UUID ownerId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        when(userRepository.existsById(subjectId)).thenReturn(true);
        when(friendshipRepository.existsFriendship(ownerId, subjectId)).thenReturn(true);
        when(permissionRepository.hasLiveLocationPermission(subjectId, ownerId)).thenReturn(false);

        CreateGeofenceRuleRequest request = new CreateGeofenceRuleRequest();
        request.setName("Test Rule");
        request.setSubjectUserIds(List.of(subjectId));
        request.setNorthEastLat(50.0);
        request.setNorthEastLon(30.0);
        request.setSouthWestLat(49.0);
        request.setSouthWestLon(29.0);
        request.setMonitorEnter(true);
        request.setMonitorLeave(true);
        request.setCooldownSeconds(120);

        assertThatThrownBy(() -> service.createRule(ownerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("live location");
    }

    @Test
    void shouldCreateRuleWithMultipleSubjects() {
        UUID ownerId = UUID.randomUUID();
        UUID friendAId = UUID.randomUUID();
        UUID friendBId = UUID.randomUUID();

        UserEntity owner = user(ownerId, "Owner", "owner@test.local");
        UserEntity friendA = user(friendAId, "Friend A", "a@test.local");
        UserEntity friendB = user(friendBId, "Friend B", "b@test.local");

        when(userRepository.existsById(ownerId)).thenReturn(true);
        when(userRepository.existsById(friendAId)).thenReturn(true);
        when(userRepository.existsById(friendBId)).thenReturn(true);
        when(friendshipRepository.existsFriendship(ownerId, friendAId)).thenReturn(true);
        when(friendshipRepository.existsFriendship(ownerId, friendBId)).thenReturn(true);
        when(permissionRepository.hasLiveLocationPermission(friendAId, ownerId)).thenReturn(true);
        when(permissionRepository.hasLiveLocationPermission(friendBId, ownerId)).thenReturn(true);
        when(entityManager.getReference(UserEntity.class, ownerId)).thenReturn(owner);
        when(entityManager.getReference(UserEntity.class, friendAId)).thenReturn(friendA);
        when(entityManager.getReference(UserEntity.class, friendBId)).thenReturn(friendB);

        CreateGeofenceRuleRequest request = new CreateGeofenceRuleRequest();
        request.setName("Home");
        request.setSubjectUserIds(List.of(ownerId, friendAId, friendBId));
        request.setNorthEastLat(50.0);
        request.setNorthEastLon(30.0);
        request.setSouthWestLat(49.0);
        request.setSouthWestLon(29.0);
        request.setMonitorEnter(true);
        request.setMonitorLeave(true);
        request.setCooldownSeconds(120);

        GeofenceRuleDto result = service.createRule(ownerId, request);

        assertThat(result.getSubjects()).hasSize(3);
        assertThat(result.getSubjects().stream().map(subject -> subject.getUserId()))
                .containsExactlyInAnyOrder(ownerId, friendAId, friendBId);
    }

    @Test
    void shouldNotRevalidateExistingSubjectsOnUpdate() {
        UUID ownerId = UUID.randomUUID();
        UUID existingSubjectId = UUID.randomUUID();

        UserEntity owner = user(ownerId, "Owner", "owner@test.local");
        UserEntity existingSubject = user(existingSubjectId, "Existing", "existing@test.local");

        GeofenceRuleEntity entity = GeofenceRuleEntity.builder()
                .id(100L)
                .ownerUser(owner)
                .name("Rule")
                .northEastLat(50.0)
                .northEastLon(30.0)
                .southWestLat(49.0)
                .southWestLon(29.0)
                .monitorEnter(true)
                .monitorLeave(true)
                .cooldownSeconds(120)
                .status(GeofenceRuleStatus.ACTIVE)
                .build();
        entity.getSubjectAssignments().add(GeofenceRuleSubjectEntity.builder()
                .id(new GeofenceRuleSubjectId(entity.getId(), existingSubjectId))
                .rule(entity)
                .subjectUser(existingSubject)
                .build());

        when(ruleRepository.findByIdAndOwner(100L, ownerId)).thenReturn(java.util.Optional.of(entity));

        UpdateGeofenceRuleRequest request = new UpdateGeofenceRuleRequest();
        request.setSubjectUserIds(List.of(existingSubjectId));

        GeofenceRuleDto result = service.updateRule(ownerId, 100L, request);

        assertThat(result.getSubjects()).hasSize(1);
        assertThat(result.getSubjects().get(0).getUserId()).isEqualTo(existingSubjectId);
        verifyNoInteractions(userRepository, friendshipRepository, permissionRepository);
    }

    private UserEntity user(UUID id, String fullName, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        return user;
    }
}
