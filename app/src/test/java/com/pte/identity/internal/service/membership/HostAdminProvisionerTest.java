package com.pte.identity.internal.service.membership;

import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostAdminProvisionerTest {

    @Test
    void createHostAdmin_generatedPassword_mustBeChangedAtFirstSignIn() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(repository.existsByUsername(anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID tenantId = UUID.randomUUID();

        HostAdminCreated created = new HostAdminProvisioner(repository, encoder)
                .createHostAdmin(tenantId, "ACME", "host@example.com");

        User user = created.user();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.getRoles()).containsExactly(Role.HOST_ADMIN);
        assertThat(user.getOrganizationId()).isEqualTo(tenantId);
        assertThat(created.generatedPassword()).isNotBlank();
    }
}
