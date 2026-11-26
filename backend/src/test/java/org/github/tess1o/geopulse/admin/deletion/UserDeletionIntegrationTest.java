package org.github.tess1o.geopulse.admin.deletion;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.admin.service.AdminUserService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.session.model.IssuedApiSession;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionContext;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.BatchProcessor;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deleting a user in the background: the request locks the user out at once, and the worker removes the data in
 * short batches (the test profile uses batches of two rows).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class UserDeletionIntegrationTest {

    private static final int GPS_POINTS = 5;

    @Inject
    UserDeletionService deletionService;

    @Inject
    UserDeletionWorker worker;

    @Inject
    AdminUserService adminUserService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    UserDeletionSteps steps;

    @Inject
    BatchProcessor batchProcessor;

    @Inject
    ImportJobService importJobService;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    UserTransaction userTransaction;

    @Test
    void deletesEveryUserScopedRowInBatchesAndKeepsTheAuditTrail() throws Exception {
        TestActor requester = admin("deletion-requester");
        TestActor user = admin("deleted-admin");
        TestActor friend = testUserFactory.createUser("deletion-friend");
        authenticationService.authenticate(user.email(), user.password());
        auditLogService.logAction(user.userId(), ActionType.SETTING_CHANGED, TargetType.SETTING, "some.key",
                Map.of("oldValue", "a", "newValue", "b"), "127.0.0.1");
        seedUserData(user.userId(), friend.userId());
        worker.drain(); // so the single steps below work on this deletion

        UserDeletionResponse requested = deletionService.requestDeletion(user.userId(), requester.userId());

        assertThat(requested.getStatus()).isEqualTo(UserDeletionStatus.QUEUED);
        assertThat(count("SELECT count(*) FROM user_sessions WHERE user_id = ? AND revoked_at IS NULL",
                user.userId())).as("sessions").isZero();
        assertThat(count("SELECT count(*) FROM user_api_tokens WHERE user_id = ?", user.userId())).isZero();
        assertThat(count("SELECT count(*) FROM gps_source_config WHERE user_id = ?", user.userId())).isZero();
        assertThat(count("SELECT count(*) FROM shared_link WHERE user_id = ?", user.userId())).isZero();
        assertThat(count("SELECT count(*) FROM user_friends WHERE user_id = ? OR friend_id = ?",
                user.userId(), user.userId())).isZero();
        assertRejected(() -> authenticationService.authenticate(user.email(), user.password()),
                ApiErrorCode.ACCESS_DENIED);
        assertThat(deletionService.requestDeletion(user.userId(), requester.userId()).getStatus())
                .as("a repeat request returns the pending deletion").isEqualTo(UserDeletionStatus.QUEUED);

        worker.step();
        assertThat(string("SELECT timeline_status FROM users WHERE id = ?", user.userId())).isEqualTo("DELETING");
        assertThat(string("SELECT is_active::text FROM users WHERE id = ?", user.userId())).isEqualTo("false");

        worker.drain();

        UserDeletionResponse finished = deletionService.find(user.userId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(UserDeletionStatus.COMPLETED);
        assertThat(finished.getDeletedGpsPoints()).isEqualTo(GPS_POINTS);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", user.userId())).isZero();
        for (String table : List.of("gps_points", "timeline_stays", "timeline_trips", "timeline_data_gaps",
                "timeline_trip_path_matches", "timeline_notes", "timeline_labels", "trips", "weather_samples",
                "weather_sample_targets", "coverage_cells", "coverage_state", "reverse_geocoding_location",
                "favorite_locations", "user_badges")) {
            assertThat(count("SELECT count(*) FROM " + table + " WHERE user_id = ?", user.userId()))
                    .as(table).isZero();
        }
        assertThat(count("SELECT count(*) FROM geofence_events WHERE owner_user_id = ? OR subject_user_id = ?",
                user.userId(), user.userId())).as("geofence events").isZero();
        assertThat(count("SELECT count(*) FROM geofence_rule_state WHERE subject_user_id = ?", user.userId()))
                .as("geofence rule state").isZero();
        assertThat(count("SELECT count(*) FROM geofence_rules WHERE owner_user_id = ?", friend.userId()))
                .as("the friend's own rule stays").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM user_invitations WHERE created_by = ?", user.userId())).isZero();

        assertThat(string("SELECT admin_email FROM audit_log WHERE action_type = 'SETTING_CHANGED' "
                + "AND admin_email = ? AND admin_user_id IS NULL", user.email()))
                .as("the deleted admin's audit entry survives, unlinked").isEqualTo(user.email());
        assertThat(count("SELECT count(*) FROM audit_log WHERE action_type = 'USER_DELETED' AND target_id = ?",
                user.userId().toString())).isEqualTo(1);
    }

    @Test
    void deletionWaitsForATimelineJobThatHoldsTheUser() throws Exception {
        TestActor requester = admin("deletion-wait-requester");
        UUID userId = testUserFactory.createUser("deletion-wait").userId();
        long gapId = insertGap(userId);
        insertGpsPoints(userId, 3);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection job = dataSource.getConnection()) {
            job.setAutoCommit(false);
            // What RealtimeTimelineProcessor and timeline generation hold for their whole run.
            try (PreparedStatement statement = job.prepareStatement(
                    "SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE")) {
                statement.setObject(1, userId);
                statement.executeQuery().close();
            }

            Future<UserDeletionResponse> request = executor.submit(
                    () -> deletionService.requestDeletion(userId, requester.userId()));
            assertThat(request.get(15, TimeUnit.SECONDS).getStatus())
                    .as("the request does not wait for the timeline job").isEqualTo(UserDeletionStatus.QUEUED);

            Future<?> step = executor.submit(() -> worker.drain());
            step.get(15, TimeUnit.SECONDS);
            assertThat(deletionService.find(userId).orElseThrow().getStatus()).isEqualTo(UserDeletionStatus.QUEUED);
            assertThat(count("SELECT count(*) FROM gps_points WHERE user_id = ?", userId))
                    .as("nothing is deleted while the job holds the user").isEqualTo(3);

            // The job goes on with its work and finishes.
            try (PreparedStatement statement = job.prepareStatement(
                    "UPDATE timeline_data_gaps SET end_time = now() WHERE id = ?")) {
                statement.setLong(1, gapId);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            job.commit();
        } finally {
            executor.shutdownNow();
        }

        makeDue(userId);
        worker.drain();

        assertThat(deletionService.find(userId).orElseThrow().getStatus()).isEqualTo(UserDeletionStatus.COMPLETED);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
    }

    @Test
    void aSecondWorkerSkipsAJobThatIsBeingWorked() throws Exception {
        TestActor requester = admin("deletion-skip-requester");
        UUID userId = testUserFactory.createUser("deletion-skip").userId();
        insertGpsPoints(userId, 3);
        deletionService.requestDeletion(userId, requester.userId());

        try (Connection otherWorker = dataSource.getConnection()) {
            otherWorker.setAutoCommit(false);
            try (PreparedStatement statement = otherWorker.prepareStatement(
                    "SELECT user_id FROM user_deletions WHERE user_id = ? FOR UPDATE")) {
                statement.setObject(1, userId);
                statement.executeQuery().close();
            }

            Future<Boolean> step;
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                step = executor.submit(() -> worker.step());
                assertThat(step.get(15, TimeUnit.SECONDS)).as("no other job to work on").isFalse();
            } finally {
                executor.shutdownNow();
            }
            otherWorker.rollback();
        }

        worker.drain();
        assertThat(deletionService.find(userId).orElseThrow().getStatus()).isEqualTo(UserDeletionStatus.COMPLETED);
    }

    @Test
    void aFailedDeletionKeepsTheUserLockedOutUntilARetrySucceeds() throws Exception {
        TestActor requester = admin("deletion-fail-requester");
        TestActor user = testUserFactory.createUser("deletion-fail");
        UUID other = testUserFactory.createUser("deletion-fail-other").userId();
        insertGpsPoints(user.userId(), 3);
        long geocodingId = insertGeocoding(user.userId());
        // Another user's stay that points at the deleted user's geocoding result blocks that phase.
        long blockingStay = insertStay(other, null, geocodingId);

        deletionService.requestDeletion(user.userId(), requester.userId());
        worker.drain();
        UserDeletionResponse afterFirstFailure = deletionService.find(user.userId()).orElseThrow();
        assertThat(afterFirstFailure.getStatus()).isEqualTo(UserDeletionStatus.RUNNING);
        assertThat(afterFirstFailure.getAttempts()).isEqualTo(1);
        assertThat(afterFirstFailure.getLastError()).isNotBlank();

        makeDue(user.userId());
        worker.drain();
        UserDeletionResponse failed = deletionService.find(user.userId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(UserDeletionStatus.FAILED);

        assertThat(deletionService.isPending(user.userId())).isTrue();
        assertRejected(() -> authenticationService.authenticate(user.email(), user.password()),
                ApiErrorCode.ACCESS_DENIED);
        assertRejected(() -> adminUserService.setUserStatus(user.userId(), true),
                ApiErrorCode.USER_DELETION_IN_PROGRESS);
        assertRejected(() -> adminUserService.changeUserRole(user.userId(), Role.ADMIN),
                ApiErrorCode.USER_DELETION_IN_PROGRESS);
        assertRejected(() -> adminUserService.resetPassword(user.userId()), ApiErrorCode.USER_DELETION_IN_PROGRESS);

        execute("DELETE FROM timeline_stays WHERE id = ?", blockingStay);
        assertThat(deletionService.retry(user.userId()).getStatus()).isEqualTo(UserDeletionStatus.RUNNING);
        worker.drain();

        assertThat(deletionService.find(user.userId()).orElseThrow().getStatus())
                .isEqualTo(UserDeletionStatus.COMPLETED);
        assertThat(count("SELECT count(*) FROM users WHERE id = ?", user.userId())).isZero();
    }

    @Test
    void aSessionCreatedRightAfterTheRequestDoesNotWork() throws Exception {
        TestActor requester = admin("deletion-session-requester");
        TestActor user = testUserFactory.createUser("deletion-session");
        deletionService.requestDeletion(user.userId(), requester.userId());

        // A sign-in that passed its checks just before the request creates its session just after it.
        IssuedApiSession session = userSessionService.createApiSession(user.user(),
                SessionContext.of(SessionAuthMethod.PASSWORD, null, null));

        assertThat(userSessionService.authenticate(session.accessToken())).as("access token").isEmpty();
        assertThatThrownBy(() -> userSessionService.refresh(session.refreshToken())).as("refresh token")
                .isInstanceOf(GeoPulseException.class);
        worker.drain();
    }

    @Test
    void aFailureReportDoesNotUndoProgressAnotherWorkerCommitted() throws Exception {
        TestActor requester = admin("deletion-race-requester");
        UUID userId = testUserFactory.createUser("deletion-race").userId();
        insertGpsPoints(userId, 3);
        worker.drain(); // so the single step below works on this deletion
        deletionService.requestDeletion(userId, requester.userId());
        worker.step();
        assertThat(deletionService.find(userId).orElseThrow().getStatus()).isEqualTo(UserDeletionStatus.RUNNING);

        long revisionOfTheFailedStep = count("SELECT revision FROM user_deletions WHERE user_id = ?", userId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection otherWorker = dataSource.getConnection()) {
            otherWorker.setAutoCommit(false);
            int otherPid;
            try (PreparedStatement statement = otherWorker.prepareStatement("SELECT pg_backend_pid()");
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                otherPid = rows.getInt(1);
            }
            // Another worker is in the middle of a step that makes progress.
            try (PreparedStatement statement = otherWorker.prepareStatement(
                    "SELECT user_id FROM user_deletions WHERE user_id = ? FOR UPDATE")) {
                statement.setObject(1, userId);
                statement.executeQuery().close();
            }
            try (PreparedStatement statement = otherWorker.prepareStatement(
                    "UPDATE user_deletions SET deleted_gps_points = deleted_gps_points + 2, revision = revision + 1 "
                            + "WHERE user_id = ?")) {
                statement.setObject(1, userId);
                statement.executeUpdate();
            }

            // Meanwhile a failure from an earlier step is reported.
            Future<?> failure = executor.submit(
                    () -> steps.recordFailure(userId, revisionOfTheFailedStep, "earlier step failed", 5,
                            Duration.ofMinutes(1)));
            awaitBlockedBy(otherPid, failure);
            otherWorker.commit();
            failure.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        UserDeletionResponse afterReport = deletionService.find(userId).orElseThrow();
        assertThat(afterReport.getDeletedGpsPoints()).as("the other worker's progress").isEqualTo(2);
        assertThat(afterReport.getAttempts()).as("the superseded failure is not counted").isZero();
        assertThat(afterReport.getStatus()).isEqualTo(UserDeletionStatus.RUNNING);
        makeDue(userId);
        worker.drain();
    }

    @Test
    void importsStopWritingForAUserBeingDeleted() throws Exception {
        TestActor requester = admin("deletion-import-requester");
        UUID userId = testUserFactory.createUser("deletion-import").userId();
        deletionService.requestDeletion(userId, requester.userId());

        UserEntity user = new UserEntity();
        user.setId(userId);
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(Instant.parse("2026-01-01T10:00:00Z"));
        point.setCoordinates(new GeometryFactory(new PrecisionModel(), 4326)
                .createPoint(new Coordinate(30.5, 50.4)));

        assertRejected(() -> batchProcessor.processBatch(List.of(point), false),
                ApiErrorCode.USER_DELETION_IN_PROGRESS);
        assertRejected(() -> importJobService.createImportJob(userId, new ImportOptions(), "points.csv",
                new byte[0]), ApiErrorCode.USER_DELETION_IN_PROGRESS);
        assertThat(count("SELECT count(*) FROM gps_points WHERE user_id = ?", userId)).isZero();
        worker.drain();
    }

    @Test
    void adminRemovalsAreCheckedOneAtATimeAgainstTheRemainingActiveAdmins() throws Exception {
        TestActor first = admin("guard-first");
        TestActor second = admin("guard-second");

        // Each check runs in a transaction that is rolled back, where these two are the only admins and the second
        // one is already being deleted: the first one may not be removed in any way.
        List<ThrowingCallable> removalsOfTheLastAdmin = List.of(
                () -> deletionService.requestDeletion(first.userId(), second.userId()),
                () -> adminUserService.changeUserRole(first.userId(), Role.USER),
                () -> adminUserService.setUserStatus(first.userId(), false));
        for (ThrowingCallable removal : removalsOfTheLastAdmin) {
            userTransaction.begin();
            try {
                execute("UPDATE users SET role = 'USER' WHERE role = 'ADMIN' AND id NOT IN (?, ?)",
                        first.userId(), second.userId());
                deletionService.requestDeletion(second.userId(), first.userId());
                assertRejected(removal, ApiErrorCode.ADMIN_USER_UPDATE_INVALID);
            } finally {
                userTransaction.rollback();
            }
        }

        // A removal waits while another one is being checked.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            int otherPid;
            try (PreparedStatement statement = other.prepareStatement("SELECT pg_backend_pid()");
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                otherPid = rows.getInt(1);
            }
            try (PreparedStatement statement = other.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, AdminMembershipGuard.LOCK_KEY);
                statement.executeQuery().close();
            }
            Future<?> demotion = executor.submit(() -> adminUserService.changeUserRole(second.userId(), Role.USER));
            awaitBlockedBy(otherPid, demotion);
            other.commit();
            demotion.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(string("SELECT role FROM users WHERE id = ?", second.userId())).isEqualTo("USER");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static void assertRejected(ThrowingCallable call, ApiErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GeoPulseException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    private TestActor admin(String prefix) throws SQLException {
        TestActor actor = testUserFactory.createUser(prefix);
        execute("UPDATE users SET role = 'ADMIN' WHERE id = ?", actor.userId());
        return actor;
    }

    private void seedUserData(UUID userId, UUID friendId) throws SQLException {
        List<Long> points = insertGpsPoints(userId, GPS_POINTS);
        long geocodingId = insertGeocoding(userId);
        long favoriteId = single("INSERT INTO favorite_locations (id, user_id, name) "
                + "VALUES (nextval('favorite_locations_id_seq'), ?, 'Home') RETURNING id", userId);
        for (int i = 0; i < 3; i++) {
            insertStay(userId, favoriteId, geocodingId);
        }
        long tripId = 0;
        for (int i = 0; i < 3; i++) {
            tripId = single("""
                    INSERT INTO timeline_trips (id, user_id, timestamp, trip_duration, last_updated, distance_meters,
                                                start_point, end_point)
                    VALUES (nextval('timeline_trips_id_seq'), ?, now() - (? * interval '1 hour'), 600, now(), 1000,
                            ST_SetSRID(ST_MakePoint(30.5, 50.4), 4326), ST_SetSRID(ST_MakePoint(30.6, 50.5), 4326))
                    RETURNING id
                    """, userId, i);
        }
        insertGap(userId);
        insertGap(userId);
        execute("""
                INSERT INTO timeline_trip_path_matches (user_id, trip_id, provider, profile, config_hash, input_hash,
                                                        status)
                VALUES (?, ?, 'test', 'car', 'c', 'i', 'MATCHED')
                """, userId, tripId);
        execute("INSERT INTO timeline_notes (user_id, content_markdown, event_time) VALUES (?, 'note', now())",
                userId);
        long labelId = single("INSERT INTO timeline_labels (user_id, name, start_time) VALUES (?, 'Trip', now()) "
                + "RETURNING id", userId);
        execute("""
                INSERT INTO trips (user_id, name, timeline_label_id, status, start_time, end_time)
                VALUES (?, 'Holiday', ?, 'COMPLETED', now() - interval '2 days', now() - interval '1 day')
                """, userId, labelId);

        long ownRule = single("""
                INSERT INTO geofence_rules (owner_user_id, name, north_east_lat, north_east_lon, south_west_lat,
                                            south_west_lon)
                VALUES (?, 'Mine', 51, 31, 50, 30) RETURNING id
                """, userId);
        long friendRule = single("""
                INSERT INTO geofence_rules (owner_user_id, name, north_east_lat, north_east_lon, south_west_lat,
                                            south_west_lon)
                VALUES (?, 'Watching', 51, 31, 50, 30) RETURNING id
                """, friendId);
        for (int i = 0; i < 3; i++) {
            insertGeofenceEvent(userId, userId, ownRule, points.get(i));
        }
        insertGeofenceEvent(friendId, userId, friendRule, points.get(3));
        execute("INSERT INTO geofence_rule_state (rule_id, subject_user_id, current_inside, last_point_id) "
                + "VALUES (?, ?, true, ?)", friendRule, userId, points.get(4));

        for (int i = 0; i < 3; i++) {
            execute("""
                    INSERT INTO weather_samples (user_id, provider, source, requested_latitude, requested_longitude,
                                                 latitude_bucket, longitude_bucket, observed_at)
                    VALUES (?, 'test', 'test', 50.4, 30.5, 50.4, 30.5, now() - (? * interval '1 hour'))
                    """, userId, i);
            execute("""
                    INSERT INTO weather_sample_targets (user_id, provider, latitude, longitude, latitude_bucket,
                                                        longitude_bucket, target_at, source)
                    VALUES (?, 'test', 50.4, 30.5, 50.4, 30.5, now() - (? * interval '1 hour'), 'test')
                    """, userId, i);
            execute("INSERT INTO coverage_cells (user_id, grid_m, cell_x, cell_y, first_seen, last_seen) "
                    + "VALUES (?, 100, ?, 1, now(), now())", userId, i);
        }
        execute("INSERT INTO coverage_state (user_id) VALUES (?)", userId);

        UUID tokenId = UUID.randomUUID();
        execute("""
                INSERT INTO user_api_tokens (id, user_id, name, token_hash, token_prefix, token_suffix)
                VALUES (?, ?, 'device', ?, 'gp_', 'abcd')
                """, tokenId, userId, UUID.randomUUID().toString());
        execute("INSERT INTO gps_source_config (id, user_id, source_type, active) VALUES (?, ?, 'OWNTRACKS', true)",
                UUID.randomUUID(), userId);
        execute("INSERT INTO shared_link (id, user_id) VALUES (?, ?)", UUID.randomUUID(), userId);
        execute("INSERT INTO user_friends (id, user_id, friend_id) VALUES (nextval('user_friends_id_seq'), ?, ?)",
                userId, friendId);
        execute("INSERT INTO user_friends (id, user_id, friend_id) VALUES (nextval('user_friends_id_seq'), ?, ?)",
                friendId, userId);
        execute("INSERT INTO user_badges (user_id, badge_id, title) VALUES (?, 'first', 'First')", userId);
        execute("INSERT INTO user_invitations (token, created_by, expires_at) VALUES (?, ?, now() + interval '1 day')",
                UUID.randomUUID().toString(), userId);
    }

    private List<Long> insertGpsPoints(UUID userId, int count) throws SQLException {
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(single("""
                    INSERT INTO gps_points (id, user_id, timestamp, coordinates)
                    VALUES (nextval('gps_points_id_seq'), ?, now() - (? * interval '1 minute'),
                            ST_SetSRID(ST_MakePoint(30.5, 50.4), 4326))
                    RETURNING id
                    """, userId, i));
        }
        return ids;
    }

    private long insertGeocoding(UUID userId) throws SQLException {
        return single("""
                INSERT INTO reverse_geocoding_location (id, user_id, request_coordinates, display_name, provider_name,
                                                        created_at)
                VALUES (nextval('reverse_geocoding_location_seq'), ?, ST_SetSRID(ST_MakePoint(30.5, 50.4), 4326),
                        'Home street', 'test', now())
                RETURNING id
                """, userId);
    }

    private long insertStay(UUID userId, Long favoriteId, Long geocodingId) throws SQLException {
        return single("""
                INSERT INTO timeline_stays (id, user_id, timestamp, stay_duration, location_name, created_at,
                                            last_updated, location, favorite_id, geocoding_id)
                VALUES (nextval('timeline_stays_id_seq'), ?, now(), 3600, 'Home', now(), now(),
                        ST_SetSRID(ST_MakePoint(30.5, 50.4), 4326), ?, ?)
                RETURNING id
                """, userId, favoriteId, geocodingId);
    }

    private long insertGap(UUID userId) throws SQLException {
        return single("""
                INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds)
                VALUES (?, now() - interval '5 hours', now(), 18000)
                RETURNING id
                """, userId);
    }

    private void insertGeofenceEvent(UUID ownerId, UUID subjectId, long ruleId, long pointId) throws SQLException {
        execute("""
                INSERT INTO geofence_events (owner_user_id, subject_user_id, rule_id, event_type, occurred_at, message,
                                             subject_display_name, point_id)
                VALUES (?, ?, ?, 'ENTER', now(), 'entered', 'Someone', ?)
                """, ownerId, subjectId, ruleId, pointId);
    }

    private void makeDue(UUID userId) throws SQLException {
        execute("UPDATE user_deletions SET next_attempt_at = now() - interval '1 second' WHERE user_id = ?", userId);
    }

    private void awaitBlockedBy(int pid, Future<?> blocked) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (count("SELECT count(*) FROM pg_stat_activity WHERE ?::int = ANY(pg_blocking_pids(pid))", pid) > 0) {
                return;
            }
            if (blocked.isDone()) {
                blocked.get();
                throw new AssertionError("The operation finished without waiting for the lock");
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The operation never waited for the lock");
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = prepare(connection, sql, parameters)) {
            statement.execute();
        }
    }

    private long single(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private long count(String sql, Object... parameters) throws SQLException {
        return single(sql, parameters);
    }

    private String string(String sql, Object... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        return statement;
    }
}
