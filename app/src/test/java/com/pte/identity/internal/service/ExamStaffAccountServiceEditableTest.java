package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The view tells the Host UI whether this account's personal details can be edited. One account belongs to exactly
 * one organization, so a staff account the Host can see is always its own and always editable.
 */
@ExtendWith(MockitoExtension.class)
class ExamStaffAccountServiceEditableTest {

    @Mock
    private UserRepository users;
    @Mock
    private OrganizationMemberDirectory directory;
    @Mock
    private UserProvisioningHelper policy;

    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

    @Test
    void aStaffAccountOfTheHostsOrganizationIsEditable() {
        ExamStaffAccountService service = new ExamStaffAccountService(users, directory, policy);
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("abc.k7m2p9qx");
        user.setEmail("examiner@example.test");
        user.setOrganizationId(host.tenantId());
        user.setRoles(new HashSet<>(Set.of(Role.EXAMINER)));
        when(directory.requireMember(host.tenantId(), user.getPublicId())).thenReturn(user);

        ExamStaffAccountView view = service.get(user.getPublicId(), host);

        assertThat(view.editable()).isTrue();
        assertThat(view.roles()).containsExactly("EXAMINER");
    }
}
