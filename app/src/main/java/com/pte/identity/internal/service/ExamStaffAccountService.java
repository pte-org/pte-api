package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.domain.User;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.dto.request.UpdateExamStaffProfileRequest;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
public class ExamStaffAccountService {
    private final OrganizationMemberRepository members;
    private final OrganizationMemberDirectory directory;
    private final UserProvisioningHelper policy;
    private final UserRepository users;

    public ExamStaffAccountService(OrganizationMemberRepository members, OrganizationMemberDirectory directory,
            UserProvisioningHelper policy, UserRepository users) {
        this.users = users;
        this.members = members;
        this.directory = directory;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public ExamStaffAccountView get(UUID id, CurrentUser caller) {
        return view(requireStaff(id, caller, false));
    }

    @Transactional
    public ExamStaffAccountView update(UUID id, UpdateExamStaffProfileRequest request, CurrentUser caller) {
        OrganizationMember member = requireStaff(id, caller, true);
        if (!directory.isExclusivelyOwned(member)) {
            throw new ForbiddenUserManagementException();
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = member.getUser();
        if (members.existsCanonicalEmail(caller.tenantId(), email, id)
                || users.existsByEmailIgnoreCaseAndDeletedFalseAndIdNot(email, user.getId())) {
            throw new EmailAlreadyUsedException();
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setDateOfBirth(request.dateOfBirth());
        return view(member);
    }

    private OrganizationMember requireStaff(UUID id, CurrentUser caller, boolean lock) {
        if (caller == null || !SecurityPolicy.hasValidScope(caller) || caller.tenantId() == null
                || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }
        OrganizationMember member = lock ? directory.requireMemberForUpdate(caller.tenantId(), id)
                : directory.requireMember(caller.tenantId(), id);
        if (!member.hasRole(Role.PROCTOR) && !member.hasRole(Role.EXAMINER)) {
            throw new UserNotFoundException();
        }
        policy.authorizeTarget(caller, member.getRoles());
        return member;
    }

    private ExamStaffAccountView view(OrganizationMember member) {
        User user = member.getUser();
        return new ExamStaffAccountView(user.getPublicId(), user.getFullName(), user.getUsername(), user.getEmail(),
                user.getPhone(), user.getDateOfBirth(), member.getRoles().stream().map(Enum::name).sorted().toList(),
                (member.isSuspended() ? UserStatus.SUSPENDED : UserStatus.ACTIVE).name(), user.isMustChangePassword(),
                !member.hasRole(Role.STUDENT));
    }
}
