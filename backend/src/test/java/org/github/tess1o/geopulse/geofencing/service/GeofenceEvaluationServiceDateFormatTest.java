package org.github.tess1o.geopulse.geofencing.service;

import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleStateRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.notifications.service.GeofenceNotificationProjectionService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeofenceEvaluationServiceDateFormatTest {

    @Mock
    GeofenceRuleRepository ruleRepository;
    @Mock
    GeofenceRuleStateRepository stateRepository;
    @Mock
    GeofenceEventRepository eventRepository;
    @Mock
    NotificationTemplateRepository templateRepository;
    @Mock
    FriendshipRepository friendshipRepository;
    @Mock
    UserFriendPermissionRepository permissionRepository;
    @Mock
    GeofenceTemplateRenderer templateRenderer;
    @Mock
    GeofenceNotificationProjectionService notificationProjectionService;

    private GeofenceEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new GeofenceEvaluationService(
                ruleRepository,
                stateRepository,
                eventRepository,
                templateRepository,
                friendshipRepository,
                permissionRepository,
                templateRenderer,
                notificationProjectionService
        );
    }

    @Test
    void shouldUseMdyFallbackWhenDateFormatMissing() throws Exception {
        UserEntity owner = new UserEntity();
        owner.setTimezone("Europe/Kyiv");
        owner.setDateFormat(null);

        String rendered = invokeFormatTimestampForOwner(Instant.parse("2026-03-26T01:31:27Z"), owner);

        assertThat(rendered).isEqualTo("03/26/2026 03:31:27");
    }

    @Test
    void shouldSupportLegacyUsAliasForDateFormat() throws Exception {
        UserEntity owner = new UserEntity();
        owner.setTimezone("Europe/Kyiv");
        owner.setDateFormat("MM/DD/YYYY");

        String rendered = invokeFormatTimestampForOwner(Instant.parse("2026-03-26T01:31:27Z"), owner);

        assertThat(rendered).isEqualTo("03/26/2026 03:31:27");
    }

    @Test
    void shouldRenderDmyWhenConfigured() throws Exception {
        UserEntity owner = new UserEntity();
        owner.setTimezone("Europe/Kyiv");
        owner.setDateFormat("DMY");

        String rendered = invokeFormatTimestampForOwner(Instant.parse("2026-03-26T01:31:27Z"), owner);

        assertThat(rendered).isEqualTo("26/03/2026 03:31:27");
    }

    private String invokeFormatTimestampForOwner(Instant timestamp, UserEntity owner) throws Exception {
        Method method = GeofenceEvaluationService.class.getDeclaredMethod("formatTimestampForOwner", Instant.class, UserEntity.class);
        method.setAccessible(true);
        return (String) method.invoke(service, timestamp, owner);
    }
}
