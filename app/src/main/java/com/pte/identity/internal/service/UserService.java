package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.dto.request.user.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.user.CreateUserRequest;
import com.pte.identity.internal.dto.request.auth.ResetPasswordRequest;
import com.pte.identity.internal.dto.request.profile.UpdateStudentProfileRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.dto.response.UserDirectoryEntryResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import org.springframework.dao.DataIntegrityViolationException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.service.user.ManagedUserResolver;
import com.pte.identity.internal.service.user.ManagedUserResolver.Target;
import com.pte.identity.internal.service.user.UserAccountProvisioner;
import com.pte.identity.internal.service.user.UserCredentialRotation;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * User provisioning + lookup, tenant-isolated. A host manages only accounts that are members of its own
 * organization; a platform admin can target an organization. Every organization account is a new account with a
 * generated username; an e-mail only has to be unique inside the organization.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationMemberDirectory memberDirectory;
    private final UserProvisioningHelper provisioningHelper;
    private final TenancyService tenancyService;
    private final RefreshTokenService refreshTokenService;
    private final ManagedUserResolver targetResolver;
    private final UserAccountProvisioner accountProvisioner;
    private final UserCredentialRotation credentialRotation;

    public UserService(UserRepository userRepository,
                       OrganizationMemberDirectory memberDirectory, PasswordEncoder passwordEncoder,
                       UserProvisioningHelper provisioningHelper, UserBulkCreateWriter bulkCreateWriter,
                       TenancyService tenancyService, ApplicationEventPublisher eventPublisher,
                       AuditLogService auditLogService, RefreshTokenService refreshTokenService,
                       StartedAttemptLookup startedAttemptLookup, Clock clock) {
        this.userRepository = userRepository;
        this.memberDirectory = memberDirectory;
        this.provisioningHelper = provisioningHelper;
        this.tenancyService = tenancyService;
        this.refreshTokenService = refreshTokenService;
        this.targetResolver = new ManagedUserResolver(userRepository, memberDirectory, provisioningHelper);
        this.accountProvisioner = new UserAccountProvisioner(userRepository, passwordEncoder,
                provisioningHelper, bulkCreateWriter, tenancyService, eventPublisher, clock);
        this.credentialRotation = new UserCredentialRotation(userRepository, passwordEncoder,
                provisioningHelper, targetResolver, eventPublisher, auditLogService, refreshTokenService,
                startedAttemptLookup);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser caller) {
        return accountProvisioner.create(request, caller);
    }

    /**
     * A within-batch duplicate email rejects the whole request (nothing written); a row whose e-mail is already a
     * member of this organization is skipped and reported. Each row runs in its own {@link UserBulkCreateWriter}
     * (REQUIRES_NEW) transaction, so a rare concurrent-duplicate race only loses that one row.
     */
    @Transactional
    public BulkCreateUsersResponse createBulk(BulkCreateUsersRequest request, CurrentUser caller) {
        return accountProvisioner.createBulk(request, caller);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId, CurrentUser caller) {
        return targetResolver.findScoped(publicId, caller, false).toResponse();
    }

    /** Updates only the profile fields allowed by the tenant student workspace. */
    @Transactional
    public UserResponse updateStudentProfile(UUID publicId, UpdateStudentProfileRequest request,
            CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }
        User user = memberDirectory.requireMemberForUpdate(caller.tenantId(), publicId);
        if (!user.hasRole(Role.STUDENT)) {
            throw new ForbiddenUserManagementException();
        }
        String email = UserAccountProvisioner.normalizeOptional(request.email());
        // A Student's e-mail only has to be unique inside the organization: the same person in another
        // organization is a separate account.
        if (email != null && userRepository.existsCanonicalEmail(caller.tenantId(),
                email.toLowerCase(Locale.ROOT), publicId)) {
            throw new EmailAlreadyUsedException();
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(UserAccountProvisioner.normalizeOptional(request.phone()));
        user.setDateOfBirth(request.dateOfBirth());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent edit to the same e-mail: the unique (organization, e-mail) index is the backstop.
            throw new EmailAlreadyUsedException();
        }
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser caller) {
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        String organizationType = user.getOrganizationId() == null ? null
                : tenancyService.findOrganizationType(user.getOrganizationId()).orElse(null);
        return UserMapper.toResponse(user, organizationType);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByTenant(CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return userRepository.findActiveByOrganization(tenantId).stream()
                .filter(member -> provisioningHelper.canManageTarget(caller, member.getRoles()))
                .map(UserMapper::toResponse)
                .toList();
    }

    /**
     * Every member of the caller's organization, including HOST_ADMIN accounts, for display purposes only (e.g.
     * resolving an audit log actor's name). It deliberately skips the {@code canManageTarget} filter that
     * {@link #listByTenant} applies: that filter answers "who may I administer", not "whose name may I see".
     */
    @Transactional(readOnly = true)
    public List<UserDirectoryEntryResponse> listDirectory(CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return userRepository.findActiveByOrganization(tenantId).stream()
                .map(user -> new UserDirectoryEntryResponse(user.getPublicId(), user.getFullName()))
                .toList();
    }

    @Transactional
    public UserResponse suspend(UUID publicId, CurrentUser caller) {
        Target target = targetResolver.findScoped(publicId, caller, true);
        target.suspend();
        refreshTokenService.revokeAllForUser(target.user().getId());
        return target.toResponse();
    }

    /** Reactivation is idempotent. */
    @Transactional
    public UserResponse reactivate(UUID publicId, CurrentUser caller) {
        Target target = targetResolver.findScoped(publicId, caller, true);
        target.reactivate();
        return target.toResponse();
    }

    @Transactional
    public UserResponse resetPassword(UUID publicId, ResetPasswordRequest request, CurrentUser caller) {
        return credentialRotation.resetPassword(publicId, request, caller);
    }

    /**
     * Generates a fresh temporary password and queues it for delivery. The previous password is intentionally not
     * recoverable because only a hash is stored. The cleartext value is returned exactly once to the authenticated
     * operator and carried only by the in-memory notification event after the transaction commits.
     */
    @Transactional
    public GeneratedCredentialsResponse sendGeneratedCredentials(UUID publicId, CurrentUser caller) {
        return credentialRotation.sendGeneratedCredentials(publicId, caller);
    }

    /**
     * Generates a fresh Student credential for Host verification without sending email. The cleartext password is
     * returned only in this response.
     */
    @Transactional
    public GeneratedCredentialsResponse generateStudentCredentials(UUID publicId, CurrentUser caller) {
        return credentialRotation.generateStudentCredentials(publicId, caller);
    }

    /** Changes the authenticated user's password and clears the first-login flag. */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request, CurrentUser caller) {
        credentialRotation.changeOwnPassword(request, caller);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listForTenant(UUID tenantId, CurrentUser caller) {
        provisioningHelper.authorizePlatformTenantListing(caller);
        return userRepository.findActiveByOrganization(tenantId).stream()
                .filter(member -> provisioningHelper.canManageTarget(caller, member.getRoles()))
                .map(UserMapper::toResponse)
                .toList();
    }
}
