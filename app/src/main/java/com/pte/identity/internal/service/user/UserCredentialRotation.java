package com.pte.identity.internal.service.user;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.auth.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.ForbiddenPasswordResetException;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.StudentAttemptInProgressException;
import com.pte.identity.internal.exception.UserEmailRequiredException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.service.OrganizationMemberDirectory;
import com.pte.identity.internal.service.RefreshTokenService;
import com.pte.identity.internal.service.UserProvisioningHelper;
import com.pte.identity.internal.service.user.ManagedUserResolver.Target;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Operator password resets, generated temporary credentials and a user changing their own password. Every change
 * leaves a Student who is mid-exam alone, and signs the account out of its other sessions.
 */
public class UserCredentialRotation {

    private final UserRepository userRepository;
    private final OrganizationMemberDirectory memberDirectory;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final ManagedUserResolver targetResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final StartedAttemptLookup startedAttemptLookup;

    public UserCredentialRotation(UserRepository userRepository, OrganizationMemberDirectory memberDirectory,
            PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
            ManagedUserResolver targetResolver, ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService, RefreshTokenService refreshTokenService,
            StartedAttemptLookup startedAttemptLookup) {
        this.userRepository = userRepository;
        this.memberDirectory = memberDirectory;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.targetResolver = targetResolver;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
        this.startedAttemptLookup = startedAttemptLookup;
    }

    public UserResponse resetPassword(UUID publicId, ResetPasswordRequest request, CurrentUser caller) {
        Target target = targetResolver.findScoped(publicId, caller, false);
        requireMayChangeCredential(caller, target);
        assignCredentialSetByAnother(target, request.newPassword());
        auditLogService.record(caller, IdentityConstants.USER_AGGREGATE, publicId.toString(),
                IdentityConstants.USER_PASSWORD_RESET, IdentityConstants.USER_PASSWORD_RESET_SUMMARY);
        return target.toResponse();
    }

    public GeneratedCredentialsResponse sendGeneratedCredentials(UUID publicId, CurrentUser caller) {
        Target target = targetResolver.findScoped(publicId, caller, false);
        requireMayChangeCredential(caller, target);
        User user = target.user();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new UserEmailRequiredException();
        }
        String temporaryPassword = rotateTemporaryPassword(target);
        eventPublisher.publishEvent(new UserCredentialsEmailRequestedEvent(
                UUID.randomUUID(), user.getPublicId(), target.organizationId(), user.getUsername(), user.getEmail(),
                user.getFullName(), temporaryPassword));
        auditLogService.record(caller, IdentityConstants.USER_AGGREGATE, publicId.toString(),
                IdentityConstants.USER_CREDENTIALS_EMAILED, IdentityConstants.USER_CREDENTIALS_EMAILED_SUMMARY);
        return new GeneratedCredentialsResponse(user.getPublicId(), user.getUsername(), user.getEmail(),
                user.getFullName(), temporaryPassword, true);
    }

    public GeneratedCredentialsResponse generateStudentCredentials(UUID publicId, CurrentUser caller) {
        Target target = targetResolver.findScoped(publicId, caller, false);
        if (!provisioningHelper.canManageTarget(caller, target.roles())
                || !target.roles().contains(Role.STUDENT) || !mayChangeCredential(caller, target)) {
            throw new ForbiddenPasswordResetException();
        }
        User user = target.user();
        String temporaryPassword = rotateTemporaryPassword(target);
        return new GeneratedCredentialsResponse(user.getPublicId(), user.getUsername(), user.getEmail(),
                user.getFullName(), temporaryPassword, false);
    }

    public void changeOwnPassword(ChangePasswordRequest request, CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            throw new UserNotFoundException();
        }
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        rejectWhileInExam(caller.hasRole(Role.STUDENT.name()), user.getPublicId());
        if (user.getCredentialHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getCredentialHash())) {
            throw new InvalidLoginException();
        }
        user.setCredentialHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        if (request.refreshToken() != null) {
            refreshTokenService.revokeAllOthers(user.getId(), request.refreshToken());
        }
        auditLogService.record(caller, IdentityConstants.USER_AGGREGATE, user.getPublicId().toString(),
                IdentityConstants.USER_PASSWORD_CHANGED_SELF, IdentityConstants.USER_PASSWORD_CHANGED_SELF_SUMMARY);
    }

    private void requireMayChangeCredential(CurrentUser caller, Target target) {
        if (!provisioningHelper.canManageTarget(caller, target.roles()) || !mayChangeCredential(caller, target)) {
            throw new ForbiddenPasswordResetException();
        }
    }

    /**
     * The credential is global to the account. A Host may change it only for an account the organization alone
     * owns; platform operators are judged by the union of the account's roles instead.
     */
    private boolean mayChangeCredential(CurrentUser caller, Target target) {
        return caller.isPlatformUser() || target.member() != null && memberDirectory.isExclusivelyOwned(target.member());
    }

    private String rotateTemporaryPassword(Target target) {
        String temporaryPassword = PasswordGenerator.generateReadable();
        assignCredentialSetByAnother(target, temporaryPassword);
        return temporaryPassword;
    }

    /** A password chosen by someone else is temporary: the owner must change it, and no old session survives. */
    private void assignCredentialSetByAnother(Target target, String rawPassword) {
        User user = target.user();
        rejectWhileInExam(target.roles().contains(Role.STUDENT), user.getPublicId());
        user.setCredentialHash(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private void rejectWhileInExam(boolean isStudent, UUID studentPublicId) {
        if (isStudent && startedAttemptLookup.hasInProgressAttempt(studentPublicId)) {
            throw new StudentAttemptInProgressException();
        }
    }
}
