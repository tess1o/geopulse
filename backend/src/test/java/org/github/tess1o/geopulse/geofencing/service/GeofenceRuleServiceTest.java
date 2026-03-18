package org.github.tess1o.geopulse.geofencing.service;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        request.setSubjectUserId(subjectId);
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
}
