package org.github.tess1o.geopulse.admin.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminUserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    SecurePasswordUtils passwordUtils;

    @Mock
    EntityManager entityManager;

    @Test
    void deleteUser_CleansInvitationReferencesBeforeDeletingUser() {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .email("invited-user@example.com")
                .role(Role.USER)
                .build();

        when(userRepository.findById(userId)).thenReturn(user);
        when(entityManager.createQuery(anyString())).thenAnswer(invocation -> {
            Query query = mock(Query.class);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.executeUpdate()).thenReturn(1);
            return query;
        });

        AdminUserService adminUserService = new AdminUserService(userRepository, passwordUtils, entityManager);
        adminUserService.deleteUser(userId);

        InOrder inOrder = inOrder(entityManager, userRepository);
        inOrder.verify(entityManager).createQuery("UPDATE UserInvitationEntity i SET i.usedBy = null WHERE i.usedBy = :userId");
        inOrder.verify(entityManager).createQuery("DELETE FROM UserInvitationEntity i WHERE i.createdBy = :userId");
        inOrder.verify(userRepository).deleteById(userId);
    }
}
