package org.github.tess1o.geopulse.admin.service;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminBootstrapServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    EntityManager entityManager;

    @Test
    void determineInitialRole_PromotesConfiguredAdminEmailAndSkipsFirstUserBootstrap() {
        AdminBootstrapService service = service(Optional.of("owner@example.com"), true);

        Role role = service.determineInitialRole("OWNER@example.com");

        assertEquals(Role.ADMIN, role);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void determineInitialRole_DoesNotPromoteFirstUserWhenExplicitAdminEmailIsConfigured() {
        AdminBootstrapService service = service(Optional.of("owner@example.com"), true);

        Role role = service.determineInitialRole("first@example.com");

        assertEquals(Role.USER, role);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void determineInitialRole_PromotesFirstRegisteredUserWhenEnabledAndNoAdminEmailIsSet() {
        AdminBootstrapService service = service(Optional.empty(), true);
        stubBootstrapLock();
        when(userRepository.count("role", Role.ADMIN)).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);

        Role role = service.determineInitialRole("first@example.com");

        assertEquals(Role.ADMIN, role);
    }

    @Test
    void determineInitialRole_DoesNotPromoteLaterUsers() {
        AdminBootstrapService service = service(Optional.empty(), true);
        stubBootstrapLock();
        when(userRepository.count("role", Role.ADMIN)).thenReturn(1L);

        Role role = service.determineInitialRole("second@example.com");

        assertEquals(Role.USER, role);
    }

    @Test
    void determineInitialRole_DoesNotPromoteWhenFirstUserBootstrapIsDisabled() {
        AdminBootstrapService service = service(Optional.empty(), false);

        Role role = service.determineInitialRole("first@example.com");

        assertEquals(Role.USER, role);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void bootstrapExistingUsers_PromotesOnlyExistingUserWhenNoAdminExists() {
        AdminBootstrapService service = service(Optional.empty(), true);
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("only@example.com")
                .role(Role.USER)
                .build();
        @SuppressWarnings("unchecked")
        PanacheQuery<UserEntity> query = mock(PanacheQuery.class);

        stubBootstrapLock();
        when(userRepository.count("role", Role.ADMIN)).thenReturn(0L);
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.findAll()).thenReturn(query);
        when(query.firstResult()).thenReturn(user);

        service.bootstrapExistingUsers();

        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void bootstrapExistingUsers_PromotesConfiguredAdminEmailWhenUserAlreadyExists() {
        AdminBootstrapService service = service(Optional.of("owner@example.com"), true);
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("OWNER@example.com")
                .role(Role.USER)
                .build();

        stubBootstrapLock();
        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user));

        service.bootstrapExistingUsers();

        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void bootstrapExistingUsers_DoesNotGuessWhenMultipleUsersExist() {
        AdminBootstrapService service = service(Optional.empty(), true);
        stubBootstrapLock();
        when(userRepository.count("role", Role.ADMIN)).thenReturn(0L);
        when(userRepository.count()).thenReturn(2L);

        service.bootstrapExistingUsers();

        verify(userRepository, never()).findAll();
    }

    @Test
    void ensureAdminForAuthenticatedUser_PromotesExistingConfiguredAdminEmail() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .email("owner@example.com")
                .role(Role.USER)
                .build();
        UserEntity managedUser = UserEntity.builder()
                .id(userId)
                .email("owner@example.com")
                .role(Role.USER)
                .build();
        AdminBootstrapService service = service(Optional.of("OWNER@example.com"), true);
        when(userRepository.findById(userId)).thenReturn(managedUser);

        service.ensureAdminForAuthenticatedUser(user);

        assertEquals(Role.ADMIN, user.getRole());
        assertEquals(Role.ADMIN, managedUser.getRole());
    }

    private AdminBootstrapService service(Optional<String> adminEmail, boolean firstUserAdminEnabled) {
        AdminBootstrapService service = new AdminBootstrapService(userRepository, entityManager);
        service.adminEmail = adminEmail;
        service.firstUserAdminEnabled = firstUserAdminEnabled;
        return service;
    }

    private void stubBootstrapLock() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")).thenReturn(query);
        when(query.setParameter(eq(1), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(null);
    }
}
