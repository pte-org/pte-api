package com.pte.identity;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHashRepository loginHashRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(userRepository, loginHashRepository, passwordEncoder);
    }

    @Test
    void findByTenantIdAndRole_delegatesToRepository() {
        UUID tenantId = UUID.randomUUID();

        User hostAdmin1 = new User();
        hostAdmin1.setPublicId(UUID.randomUUID());
        hostAdmin1.setEmail("admin1@example.com");

        User hostAdmin2 = new User();
        hostAdmin2.setPublicId(UUID.randomUUID());
        hostAdmin2.setEmail("admin2@example.com");

        when(userRepository.findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of(hostAdmin1, hostAdmin2));

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(hostAdmin1, hostAdmin2);
        verify(userRepository).findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN);
    }

    @Test
    void findByTenantIdAndRole_emptyResult() {
        UUID tenantId = UUID.randomUUID();

        when(userRepository.findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN))
                .thenReturn(List.of());

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.HOST_ADMIN);

        assertThat(result).isEmpty();
        verify(userRepository).findByTenantIdAndRolesContaining(tenantId, Role.HOST_ADMIN);
    }

    @Test
    void findByTenantIdAndRole_multipleRoles() {
        UUID tenantId = UUID.randomUUID();

        User student1 = new User();
        student1.setPublicId(UUID.randomUUID());
        student1.setEmail("student1@example.com");

        User student2 = new User();
        student2.setPublicId(UUID.randomUUID());
        student2.setEmail("student2@example.com");

        when(userRepository.findByTenantIdAndRolesContaining(tenantId, Role.STUDENT))
                .thenReturn(List.of(student1, student2));

        List<User> result = service.findByTenantIdAndRole(tenantId, Role.STUDENT);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(student1, student2);
    }

    @Test
    void countStudents_delegatesToRoleAwareRepositoryQuery() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.countByTenantIdAndRole(tenantId, Role.STUDENT)).thenReturn(487L);

        assertThat(service.countStudents(tenantId)).isEqualTo(487L);

        verify(userRepository).countByTenantIdAndRole(tenantId, Role.STUDENT);
    }
}
