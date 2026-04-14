package org.github.tess1o.geopulse.geofencing.service;

import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceEventRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleStateRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.notifications.service.GeofenceNotificationProjectionService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class GeofenceEvaluationServiceDateFormatTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

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

    @Test
    void shouldRender12HourTimeWhenConfigured() throws Exception {
        UserEntity owner = new UserEntity();
        owner.setTimezone("Europe/Kyiv");
        owner.setDateFormat("YMD");
        owner.setTimeFormat("12h");

        String rendered = invokeFormatTimestampForOwner(Instant.parse("2026-03-26T01:31:27Z"), owner);

        assertThat(rendered).isEqualTo("2026-03-26 3:31:27 AM");
    }

    @Test
    void shouldFallbackTo24HourTimeWhenTimeFormatInvalid() throws Exception {
        UserEntity owner = new UserEntity();
        owner.setTimezone("Europe/Kyiv");
        owner.setDateFormat("YMD");
        owner.setTimeFormat("unexpected");

        String rendered = invokeFormatTimestampForOwner(Instant.parse("2026-03-26T01:31:27Z"), owner);

        assertThat(rendered).isEqualTo("2026-03-26 03:31:27");
    }

    @Test
    void shouldPersistSubjectDisplayNameSnapshotOnEventCreation() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setTimezone("UTC");

        UserEntity subject = new UserEntity();
        subject.setId(UUID.randomUUID());
        subject.setFullName(null);
        subject.setEmail("subject@example.com");

        GeofenceRuleEntity rule = new GeofenceRuleEntity();
        rule.setId(10L);
        rule.setName("Home");
        rule.setOwnerUser(owner);

        GpsPointEntity point = new GpsPointEntity();
        point.setUser(subject);
        point.setTimestamp(Instant.parse("2026-03-24T10:00:00Z"));
        point.setCoordinates(GEOMETRY_FACTORY.createPoint(new Coordinate(30.5234, 50.4501)));

        NotificationTemplateEntity inAppTemplate = NotificationTemplateEntity.builder()
                .id(71L)
                .name("In-app")
                .enabled(true)
                .sendInApp(true)
                .build();
        UserEntity templateOwner = new UserEntity();
        templateOwner.setId(ownerId);
        inAppTemplate.setUser(templateOwner);

        when(templateRepository.findDefaultEnterByUser(ownerId)).thenReturn(Optional.of(inAppTemplate));

        Method method = GeofenceEvaluationService.class.getDeclaredMethod(
                "emitEvent",
                GeofenceRuleEntity.class,
                UserEntity.class,
                GpsPointEntity.class,
                GeofenceEventType.class
        );
        method.setAccessible(true);
        method.invoke(service, rule, subject, point, GeofenceEventType.ENTER);

        ArgumentCaptor<GeofenceEventEntity> captor = ArgumentCaptor.forClass(GeofenceEventEntity.class);
        verify(eventRepository).persist(captor.capture());
        GeofenceEventEntity persisted = captor.getValue();
        assertThat(persisted.getSubjectDisplayName()).isEqualTo("subject@example.com");
        verify(notificationProjectionService).publishSnapshot(persisted, Map.of(
                "ruleId", 10L,
                "ruleName", "Home",
                "subjectUserId", subject.getId(),
                "subjectDisplayName", "subject@example.com",
                "eventCode", "ENTER",
                "eventVerb", "entered",
                "lat", 50.4501,
                "lon", 30.5234
        ));
    }

    @Test
    void shouldNotPublishInAppWhenNoTemplateResolved() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setTimezone("UTC");

        UserEntity subject = new UserEntity();
        subject.setId(UUID.randomUUID());
        subject.setFullName("Subject Name");
        subject.setEmail("subject@example.com");

        GeofenceRuleEntity rule = new GeofenceRuleEntity();
        rule.setId(30L);
        rule.setName("No template rule");
        rule.setOwnerUser(owner);

        GpsPointEntity point = new GpsPointEntity();
        point.setUser(subject);
        point.setTimestamp(Instant.parse("2026-03-24T13:00:00Z"));
        point.setCoordinates(GEOMETRY_FACTORY.createPoint(new Coordinate(30.5234, 50.4501)));

        when(templateRepository.findDefaultEnterByUser(ownerId)).thenReturn(Optional.empty());

        Method method = GeofenceEvaluationService.class.getDeclaredMethod(
                "emitEvent",
                GeofenceRuleEntity.class,
                UserEntity.class,
                GpsPointEntity.class,
                GeofenceEventType.class
        );
        method.setAccessible(true);
        method.invoke(service, rule, subject, point, GeofenceEventType.ENTER);

        verify(eventRepository).persist(org.mockito.ArgumentMatchers.any(GeofenceEventEntity.class));
        verify(notificationProjectionService, never()).publishSnapshot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void shouldSkipInAppProjectionWhenTemplateDisablesInApp() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);
        owner.setTimezone("UTC");

        UserEntity subject = new UserEntity();
        subject.setId(UUID.randomUUID());
        subject.setFullName("Subject Name");
        subject.setEmail("subject@example.com");

        NotificationTemplateEntity template = NotificationTemplateEntity.builder()
                .id(99L)
                .name("External Only")
                .destination("discord://token")
                .enabled(true)
                .sendInApp(false)
                .build();
        UserEntity templateOwner = new UserEntity();
        templateOwner.setId(ownerId);
        template.setUser(templateOwner);

        GeofenceRuleEntity rule = new GeofenceRuleEntity();
        rule.setId(20L);
        rule.setName("Office");
        rule.setOwnerUser(owner);
        rule.setEnterTemplate(template);

        GpsPointEntity point = new GpsPointEntity();
        point.setUser(subject);
        point.setTimestamp(Instant.parse("2026-03-24T12:00:00Z"));
        point.setCoordinates(GEOMETRY_FACTORY.createPoint(new Coordinate(30.5234, 50.4501)));

        Method method = GeofenceEvaluationService.class.getDeclaredMethod(
                "emitEvent",
                GeofenceRuleEntity.class,
                UserEntity.class,
                GpsPointEntity.class,
                GeofenceEventType.class
        );
        method.setAccessible(true);
        method.invoke(service, rule, subject, point, GeofenceEventType.ENTER);

        verify(eventRepository).persist(org.mockito.ArgumentMatchers.any(GeofenceEventEntity.class));
        verify(notificationProjectionService, never()).publishSnapshot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
    }

    private String invokeFormatTimestampForOwner(Instant timestamp, UserEntity owner) throws Exception {
        Method method = GeofenceEvaluationService.class.getDeclaredMethod("formatTimestampForOwner", Instant.class, UserEntity.class);
        method.setAccessible(true);
        return (String) method.invoke(service, timestamp, owner);
    }
}
