package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.dto.request.profile.UpdateExamStaffProfileRequest;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamStaffAccountServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private OrganizationMemberDirectory directory;

    @Mock
    private UserProvisioningHelper policy;

    private ExamStaffAccountService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private CurrentUser host;
    private User staff;

    @BeforeEach
    void setUp() {
        service = new ExamStaffAccountService(users, directory, policy);
        host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        staff = new User();
        staff.setPublicId(staffId);
        staff.setUsername("acme.k7m2p9qx");
        staff.setEmail("old@acme.example");
        staff.setFullName("Old Name");
        staff.setOrganizationId(tenantId);
        staff.setRoles(new HashSet<>(Set.of(Role.EXAMINER)));
        lenient().when(directory.requireMemberForUpdate(tenantId, staffId)).thenReturn(staff);
        lenient().when(directory.requireMember(tenantId, staffId)).thenReturn(staff);
    }

    private UpdateExamStaffProfileRequest request(String email) {
        return new UpdateExamStaffProfileRequest("New Name", email, null, null);
    }

    @Test
    void update_emailIsCheckedOnlyInsideTheCallersOrganizationAndExcludesTheEditedAccount() {
        when(users.existsCanonicalEmail(tenantId, "new@acme.example", staffId)).thenReturn(false);

        ExamStaffAccountView view = service.update(staffId, request("  New@Acme.Example "), host);

        verify(users).existsCanonicalEmail(tenantId, "new@acme.example", staffId);
        assertThat(staff.getEmail()).isEqualTo("new@acme.example");
        assertThat(view.username()).isEqualTo("acme.k7m2p9qx");
    }

    @Test
    void update_emailUsedByAnotherAccountOfTheOrganization_isRejectedAndNothingChanges() {
        when(users.existsCanonicalEmail(tenantId, "taken@acme.example", staffId)).thenReturn(true);

        assertThatThrownBy(() -> service.update(staffId, request("Taken@acme.example"), host))
                .isInstanceOf(EmailAlreadyUsedException.class);
        assertThat(staff.getEmail()).isEqualTo("old@acme.example");
        assertThat(staff.getFullName()).isEqualTo("Old Name");
    }

    @Test
    void update_neverChangesTheUsername() {
        when(users.existsCanonicalEmail(any(), any(), any())).thenReturn(false);

        service.update(staffId, request("new@acme.example"), host);

        assertThat(staff.getUsername()).isEqualTo("acme.k7m2p9qx");
    }

    @Test
    void get_returnsTheAccountWithItsRoles() {
        ExamStaffAccountView view = service.get(staffId, host);

        assertThat(view.publicId()).isEqualTo(staffId);
        assertThat(view.roles()).containsExactly("EXAMINER");
    }

    @Test
    void update_accountOfAnotherOrganization_isNotFound() {
        UUID foreignStaffId = UUID.randomUUID();
        when(directory.requireMemberForUpdate(tenantId, foreignStaffId)).thenThrow(new UserNotFoundException());

        assertThatThrownBy(() -> service.update(foreignStaffId, request("new@acme.example"), host))
                .isInstanceOf(UserNotFoundException.class);
        verify(users, never()).existsCanonicalEmail(any(), any(), any());
    }

    @Test
    void update_studentAccount_isNotFoundBecauseItIsNotExamStaff() {
        staff.setRoles(new HashSet<>(Set.of(Role.STUDENT)));

        assertThatThrownBy(() -> service.update(staffId, request("new@acme.example"), host))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(staff.getEmail()).isEqualTo("old@acme.example");
    }

    @Test
    void update_callerWithoutAnOrganization_isForbidden() {
        CurrentUser platform = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() -> service.update(staffId, request("new@acme.example"), platform))
                .isInstanceOf(ForbiddenUserManagementException.class);
    }
}
