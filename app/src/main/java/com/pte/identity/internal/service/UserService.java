package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.CreateUserRequest;
import com.pte.identity.internal.dto.request.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.CreatedUser;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.RowError;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.DuplicateEmailInBatchException;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenPasswordResetException;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.UsernameGenerator;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * User provisioning + lookup, tenant-isolated. A host creates users only within
 * its own tenant; a platform admin can target a tenant.
 *
 * <p>Ported from {@code services/iam}'s {@code UserService}. Two changes from
 * the microservice version, both required by plan.md's global constraints:
 * <ul>
 *   <li>No synchronization message writes on create/suspend/reactivate/resetPassword — those
 *       existed only to notify other services (admin's now-deleted roster
 *       read model, notification's email trigger) across a process boundary
 *       that no longer exists here.</li>
 *   <li>{@link #me} always returns a null {@code organizationType} for now
 *       instead of reading a locally-cached {@code TenantRegistry} kept in
 *       sync via RabbitMQ from admin's Tenant/Organization data — the same
 *       kind of cross-service read model as the roster bug Phase 03 removes,
 *       just discovered a phase earlier. Once Phase 03 ports {@code tenancy},
 *       this becomes a direct in-process call.</li>
 * </ul>
 */
@Service
public class UserService {

    /** Roles a tenant-scoped caller (HOST_ADMIN) may reset — rescuing a locked-out Student/Proctor, not a peer admin. */
    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final UserBulkCreateWriter bulkCreateWriter;
    private final TenancyService tenancyService;

    public UserService(UserRepository userRepository, LoginHashRepository loginHashRepository,
                       PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
                       UserBulkCreateWriter bulkCreateWriter, TenancyService tenancyService) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.bulkCreateWriter = bulkCreateWriter;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser caller) {
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());
        Set<Role> roles = provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles());
        provisioningHelper.validateTenantScope(caller, tenantId, roles);

        // username = email for every role created through this single-user
        // endpoint (plans/quang-tenant-commercialization Phase 1) — including
        // STUDENT for now; per-tenant student username generation only
        // applies to the bulk roster-import path (Phase 8), not here. Since
        // username carries the real DB uniqueness constraint now, duplicate
        // detection checks it, not email.
        if (userRepository.existsByUsernameAndTenantId(request.email(), tenantId)) {
            throw new EmailAlreadyUsedException();
        }
        if (roles.contains(Role.STUDENT)) {
            tenancyService.assertCanAddStudents(tenantId, 1L);
        }

        User user = new User();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setTenantId(tenantId);
        user.setRoles(roles);
        user.setStudentCode(request.studentCode());
        user.setClassName(request.className());
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        // saveAndFlush kept from the microservice version: callers of
        // IdentityService.findById() immediately after create() need the
        // Hibernate-generated createdAt, e.g. enrollment's roster sort key
        // (Phase 03) — not an synchronization message concern, a same-transaction read concern.
        User saved = userRepository.saveAndFlush(user);

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(saved.getId());
        loginHash.setHash(passwordEncoder.encode(request.password()));
        loginHashRepository.save(loginHash);

        return UserMapper.toResponse(saved);
    }

    /**
     * A within-batch duplicate email rejects the whole request (nothing
     * written); a conflict with an EXISTING user just skips that row and
     * reports it. Each row runs in its own {@link UserBulkCreateWriter}
     * (REQUIRES_NEW) transaction, so a rare concurrent-duplicate race only
     * loses that one row.
     */
    @Transactional
    public BulkCreateUsersResponse createBulk(BulkCreateUsersRequest request, CurrentUser caller) {
        provisioningHelper.authorizeBulkStudentCreation(caller);
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());

        Set<String> seenInBatch = new HashSet<>();
        for (BulkCreateUserRow row : request.rows()) {
            String email = normalizeOptional(row.email());
            if (email != null && !seenInBatch.add(email)) {
                throw new DuplicateEmailInBatchException();
            }
        }

        List<BulkCreateUserRow> rows = request.rows();
        List<String> emails = rows.stream()
                .map(BulkCreateUserRow::email)
                .map(UserService::normalizeOptional)
                .filter(email -> email != null)
                .toList();
        Set<String> existingEmails = emails.isEmpty()
                ? Set.of()
                : new HashSet<>(userRepository.findByTenantIdAndEmailIn(tenantId, emails)
                        .stream().map(User::getEmail).map(UserService::normalizeOptional).toList());

        long adding = rows.stream()
                .filter(row -> {
                    String email = normalizeOptional(row.email());
                    return email == null || !existingEmails.contains(email);
                })
                .count();
        if (adding > 0L) {
            tenancyService.assertCanAddStudents(tenantId, adding);
        }

        List<CreatedUser> created = new ArrayList<>();
        List<RowError> skipped = new ArrayList<>();

        String tenantCode = null;
        for (int i = 0; i < rows.size(); i++) {
            BulkCreateUserRow row = rows.get(i);
            int rowIndex = i;
            String email = normalizeOptional(row.email());
            String fullName = normalizeOptional(row.fullName());
            UserBulkCreateWriter.Row writerRow = new UserBulkCreateWriter.Row(
                    email, fullName, row.studentCode(), row.className(), row.phone(), row.dateOfBirth());
            if (email != null && existingEmails.contains(email)) {
                skipped.add(new RowError(rowIndex, email, IdentityConstants.EMAIL_ALREADY_USED));
                continue;
            }

            Optional<UserBulkCreateWriter.Result> result;
            if (email == null) {
                if (tenantCode == null) {
                    tenantCode = tenancyService.getTenantCode(tenantId);
                }
                result = createGeneratedStudentWithRetry(writerRow, tenantId, tenantCode);
            } else {
                result = bulkCreateWriter.createOne(writerRow, tenantId);
            }

            result
                    .ifPresentOrElse(
                            createdResult -> created.add(new CreatedUser(createdResult.user().getPublicId(),
                                    createdResult.user().getUsername(), createdResult.user().getEmail(),
                                    createdResult.user().getFullName(),
                                    createdResult.generatedPassword())),
                            () -> skipped.add(new RowError(rowIndex, email,
                                    email == null ? IdentityConstants.ROSTER_IMPORT_FAILED
                                            : IdentityConstants.EMAIL_ALREADY_USED)));
        }

        return new BulkCreateUsersResponse(created, skipped);
    }

    private Optional<UserBulkCreateWriter.Result> createGeneratedStudentWithRetry(
            UserBulkCreateWriter.Row row, UUID tenantId, String tenantCode) {
        for (int attempt = 0; attempt < IdentityConstants.ROSTER_USERNAME_COLLISION_RETRIES; attempt++) {
            Optional<UserBulkCreateWriter.Result> result = bulkCreateWriter.createGeneratedStudent(
                    UsernameGenerator.generate(tenantCode), row, tenantId);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId, CurrentUser caller) {
        return UserMapper.toResponse(findScoped(publicId, caller));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser caller) {
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        String organizationType = tenancyService.findOrganizationType(user.getTenantId()).orElse(null);
        return UserMapper.toResponse(user, organizationType);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByTenant(CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return userRepository.findByTenantId(tenantId).stream()
                .filter(user -> provisioningHelper.canManageTarget(caller, user.getRoles()))
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse suspend(UUID publicId, CurrentUser caller) {
        User user = findScoped(publicId, caller);
        user.suspend();
        return UserMapper.toResponse(user);
    }

    /** Reactivation is idempotent. */
    @Transactional
    public UserResponse reactivate(UUID publicId, CurrentUser caller) {
        User user = findScoped(publicId, caller);
        if (user.isSuspended()) {
            user.reactivate();
        }
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse resetPassword(UUID publicId, ResetPasswordRequest request, CurrentUser caller) {
        User user = findScoped(publicId, caller);
        if (!provisioningHelper.canManageTarget(caller, user.getRoles())) {
            throw new ForbiddenPasswordResetException();
        }
        LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                .orElseThrow(UserNotFoundException::new);
        loginHash.setHash(passwordEncoder.encode(request.newPassword()));
        loginHashRepository.save(loginHash);

        return UserMapper.toResponse(user);
    }

    /** Changes the authenticated user's password and clears the first-login flag. */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request, CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            throw new UserNotFoundException();
        }
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                .orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(request.currentPassword(), loginHash.getHash())) {
            throw new InvalidLoginException();
        }
        loginHash.setHash(passwordEncoder.encode(request.newPassword()));
        loginHashRepository.save(loginHash);
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listForTenant(UUID tenantId, CurrentUser caller) {
        provisioningHelper.authorizePlatformTenantListing(caller);
        return userRepository.findByTenantId(tenantId).stream()
                .filter(user -> provisioningHelper.canManageTarget(caller, user.getRoles()))
                .map(UserMapper::toResponse)
                .toList();
    }

    private User findScoped(UUID publicId, CurrentUser caller) {
        User user = caller.isPlatformUser()
                ? userRepository.findByPublicId(publicId).orElseThrow(UserNotFoundException::new)
                : userRepository.findByPublicIdAndTenantId(publicId, caller.tenantId())
                        .orElseThrow(UserNotFoundException::new);
        provisioningHelper.authorizeTarget(caller, user.getRoles());
        return user;
    }
}
