package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.CreateUserRequest;
import com.pte.identity.internal.dto.request.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.CreatedUser;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.RowError;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.DuplicateEmailInBatchException;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenPasswordResetException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User provisioning + lookup, tenant-isolated. A host creates users only within
 * its own tenant; a platform admin can target a tenant.
 *
 * <p>Ported from {@code services/iam}'s {@code UserService}. Two changes from
 * the microservice version, both required by plan.md's global constraints:
 * <ul>
 *   <li>No outbox writes on create/suspend/reactivate/resetPassword — those
 *       existed only to notify other services (admin's now-deleted roster
 *       projection, notification's email trigger) across a process boundary
 *       that no longer exists here.</li>
 *   <li>{@link #me} always returns a null {@code organizationType} for now
 *       instead of reading a locally-cached {@code TenantRegistry} kept in
 *       sync via RabbitMQ from admin's Tenant/Organization data — the same
 *       kind of cross-service projection as the roster bug Phase 03 removes,
 *       just discovered a phase earlier. Once Phase 03 ports {@code tenancy},
 *       this becomes a direct in-process call.</li>
 * </ul>
 */
@Service
public class UserService {

    /** Roles a tenant-scoped caller (HOST_ADMIN) may reset — rescuing a locked-out Student/Proctor, not a peer admin. */
    private static final Set<Role> HOST_RESETTABLE_ROLES = Set.of(Role.STUDENT, Role.PROCTOR);

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final UserBulkCreateWriter bulkCreateWriter;

    public UserService(UserRepository userRepository, LoginHashRepository loginHashRepository,
                       PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
                       UserBulkCreateWriter bulkCreateWriter) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.bulkCreateWriter = bulkCreateWriter;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser caller) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException();
        }
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());
        Set<Role> roles = provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles());

        User user = new User();
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
        // (Phase 03) — not an outbox concern, a same-transaction read concern.
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
    public BulkCreateUsersResponse createBulk(BulkCreateUsersRequest request, CurrentUser caller) {
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());

        Set<String> seenInBatch = new HashSet<>();
        for (BulkCreateUserRow row : request.rows()) {
            if (!seenInBatch.add(row.email())) {
                throw new DuplicateEmailInBatchException();
            }
        }

        List<String> emails = request.rows().stream().map(BulkCreateUserRow::email).toList();
        Set<String> existingEmails = new HashSet<>(
                userRepository.findByEmailIn(emails).stream().map(User::getEmail).toList());

        List<CreatedUser> created = new ArrayList<>();
        List<RowError> skipped = new ArrayList<>();

        List<BulkCreateUserRow> rows = request.rows();
        for (int i = 0; i < rows.size(); i++) {
            BulkCreateUserRow row = rows.get(i);
            int rowIndex = i;
            if (existingEmails.contains(row.email())) {
                skipped.add(new RowError(rowIndex, row.email(), IdentityConstants.EMAIL_ALREADY_USED));
                continue;
            }
            UserBulkCreateWriter.Row writerRow = new UserBulkCreateWriter.Row(
                    row.email(), row.fullName(), row.studentCode(), row.className(),
                    row.phone(), row.dateOfBirth());
            bulkCreateWriter.createOne(writerRow, tenantId)
                    .ifPresentOrElse(
                            result -> created.add(new CreatedUser(result.user().getPublicId(),
                                    result.user().getEmail(), result.user().getFullName(),
                                    result.generatedPassword())),
                            () -> skipped.add(new RowError(rowIndex, row.email(), IdentityConstants.EMAIL_ALREADY_USED)));
        }

        return new BulkCreateUsersResponse(created, skipped);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId, CurrentUser caller) {
        return UserMapper.toResponse(findScoped(publicId, caller));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser caller) {
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        // TODO(Phase 03): call tenancy's public service in-process for the real
        // organizationType once `tenancy` is ported. See class javadoc.
        return UserMapper.toResponse(user, null);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByTenant(CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return userRepository.findByTenantId(tenantId).stream().map(UserMapper::toResponse).toList();
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
        if (!caller.isPlatformUser() && !HOST_RESETTABLE_ROLES.containsAll(user.getRoles())) {
            throw new ForbiddenPasswordResetException();
        }
        LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                .orElseThrow(UserNotFoundException::new);
        loginHash.setHash(passwordEncoder.encode(request.newPassword()));
        loginHashRepository.save(loginHash);

        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listForTenant(UUID tenantId) {
        return userRepository.findByTenantId(tenantId).stream().map(UserMapper::toResponse).toList();
    }

    private User findScoped(UUID publicId, CurrentUser caller) {
        if (caller.isPlatformUser()) {
            return userRepository.findByPublicId(publicId).orElseThrow(UserNotFoundException::new);
        }
        return userRepository.findByPublicIdAndTenantId(publicId, caller.tenantId())
                .orElseThrow(UserNotFoundException::new);
    }
}
