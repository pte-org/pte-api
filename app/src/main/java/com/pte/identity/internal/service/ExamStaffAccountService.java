package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.dto.request.profile.UpdateExamStaffProfileRequest;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import org.springframework.dao.DataIntegrityViolationException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;

@Service
public class ExamStaffAccountService {
    private final UserRepository users;
    private final OrganizationMemberDirectory directory;
    private final UserProvisioningHelper policy;

    public ExamStaffAccountService(UserRepository users, OrganizationMemberDirectory directory,
            UserProvisioningHelper policy) {
        this.users = users;
        this.directory = directory;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public ExamStaffAccountView get(UUID id, CurrentUser caller) {
        return view(requireStaff(id, caller, false));
    }

    @Transactional
    public ExamStaffAccountView update(UUID id, UpdateExamStaffProfileRequest request, CurrentUser caller) {
        User user = requireStaff(id, caller, true);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsCanonicalEmail(caller.tenantId(), email, id)) {
            throw new EmailAlreadyUsedException();
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setDateOfBirth(request.dateOfBirth());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent edit to the same e-mail: the unique (organization, e-mail) index is the backstop.
            throw new EmailAlreadyUsedException();
        }
        return view(user);
    }

    private User requireStaff(UUID id, CurrentUser caller, boolean lock) {
        if (caller == null || !SecurityPolicy.hasValidScope(caller) || caller.tenantId() == null
                || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }
        User user = lock ? directory.requireMemberForUpdate(caller.tenantId(), id)
                : directory.requireMember(caller.tenantId(), id);
        if (!user.hasRole(Role.PROCTOR) && !user.hasRole(Role.EXAMINER)) {
            throw new UserNotFoundException();
        }
        policy.authorizeTarget(caller, user.getRoles());
        return user;
    }

    private ExamStaffAccountView view(User user) {
        return new ExamStaffAccountView(user.getPublicId(), user.getFullName(), user.getUsername(), user.getEmail(),
                user.getPhone(), user.getDateOfBirth(), user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.getStatus().name(), user.isMustChangePassword(), !user.hasRole(Role.STUDENT));
    }
}
