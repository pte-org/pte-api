package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.dto.request.UpdateExamStaffProfileRequest;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
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
    private final UserRepository users;
    private final UserProvisioningHelper policy;

    public ExamStaffAccountService(UserRepository users, UserProvisioningHelper policy) {
        this.users = users;
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
        } catch (DataIntegrityViolationException exception) {
            // Only this index represents an expected email collision; other integrity failures remain errors.
            if (exception.getMostSpecificCause().getMessage() != null
                    && exception.getMostSpecificCause().getMessage().contains("uk_users_tenant_canonical_email")) {
                throw new EmailAlreadyUsedException();
            }
            throw exception;
        }
        return view(user);
    }

    private User requireStaff(UUID id, CurrentUser caller, boolean lock) {
        if (caller == null || !SecurityPolicy.hasValidScope(caller) || caller.tenantId() == null
                || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }
        User user = (lock ? users.findWithLockByPublicIdAndTenantId(id, caller.tenantId())
                : users.findByPublicIdAndTenantId(id, caller.tenantId()))
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> candidate.getRoles().contains(Role.PROCTOR)
                        || candidate.getRoles().contains(Role.EXAMINER))
                .orElseThrow(UserNotFoundException::new);
        policy.authorizeTarget(caller, user.getRoles());
        return user;
    }

    private ExamStaffAccountView view(User user) {
        return new ExamStaffAccountView(user.getPublicId(), user.getFullName(), user.getUsername(), user.getEmail(),
                user.getPhone(), user.getDateOfBirth(), user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.getStatus().name(), user.isMustChangePassword(), !user.getRoles().contains(Role.STUDENT));
    }
}
