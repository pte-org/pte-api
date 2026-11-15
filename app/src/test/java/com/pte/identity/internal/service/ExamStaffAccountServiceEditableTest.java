package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The view tells the Host UI whether this account's personal details can be edited from this organization. */
@ExtendWith(MockitoExtension.class)
class ExamStaffAccountServiceEditableTest {

    @Mock
    private OrganizationMemberRepository members;
    @Mock
    private OrganizationMemberDirectory directory;
    @Mock
    private UserProvisioningHelper policy;
    @Mock
    private UserRepository users;

    private ExamStaffAccountService service;
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
    private OrganizationMember member;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        service = new ExamStaffAccountService(members, directory, policy, users);
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("examiner.one");
        user.setEmail("examiner@example.test");
        member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(host.tenantId());
        member.setRoles(new HashSet<>(Set.of(Role.EXAMINER)));
        staffId = user.getPublicId();
        when(directory.requireMember(host.tenantId(), staffId)).thenReturn(member);
    }

    @Test
    void anAccountOwnedByOneOrganizationIsEditable() {
        when(directory.isExclusivelyOwned(member)).thenReturn(true);

        ExamStaffAccountView view = service.get(staffId, host);

        assertThat(view.editable()).isTrue();
    }

    @Test
    void anAccountSharedWithAnotherOrganizationIsNotEditable() {
        when(directory.isExclusivelyOwned(member)).thenReturn(false);

        ExamStaffAccountView view = service.get(staffId, host);

        assertThat(view.editable()).isFalse();
        assertThat(view.roles()).containsExactly("EXAMINER");
    }
}
