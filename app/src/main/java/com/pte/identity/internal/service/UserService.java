package com.pte.identity.internal.service;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.CreateUserRequest;
import com.pte.identity.internal.dto.request.ResetPasswordRequest;
import com.pte.identity.internal.dto.request.UpdateStudentProfileRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.CreatedUser;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.RowError;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.dto.response.UserDirectoryEntryResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.DuplicateEmailInBatchException;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenPasswordResetException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.InvalidLoginException;
import com.pte.identity.internal.exception.StudentCredentialEmailNotAllowedException;
import com.pte.identity.internal.exception.UserEmailRequiredException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.identity.internal.util.UsernameGenerator;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * User provisioning + lookup, tenant-isolated. A host manages only accounts that are members of its own
 * organization; a platform admin can target an organization. An e-mail that already belongs to an account joins
 * that account to the organization instead of creating a second account.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationMemberDirectory memberDirectory;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final UserBulkCreateWriter bulkCreateWriter;
    private final TenancyService tenancyService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, OrganizationMemberRepository memberRepository,
                       OrganizationMemberDirectory memberDirectory, PasswordEncoder passwordEncoder,
                       UserProvisioningHelper provisioningHelper, UserBulkCreateWriter bulkCreateWriter,
                       TenancyService tenancyService, ApplicationEventPublisher eventPublisher,
                       AuditLogService auditLogService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.memberDirectory = memberDirectory;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.bulkCreateWriter = bulkCreateWriter;
        this.tenancyService = tenancyService;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser caller) {
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());
        Set<Role> roles = provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles());
        provisioningHelper.validateTenantScope(caller, tenantId, roles);
        // username = email for every role created through this single-user endpoint; per-organization student
        // username generation only applies to the bulk roster-import path.
        Optional<User> existing = userRepository.findByUsername(request.email());
        // A platform account never joins an organization, and an e-mail held by another live account under a
        // different username would make e-mail based identity resolution ambiguous.
        if (existing.isPresent() && (existing.get().isDeleted() || existing.get().hasPlatformRole()
                || memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(
                        existing.get().getId(), tenantId).isPresent())
                || existing.isEmpty() && userRepository.existsByEmailIgnoreCaseAndDeletedFalse(request.email())) {
            throw new EmailAlreadyUsedException();
        }
        if (roles.contains(Role.STUDENT)) {
            tenancyService.assertCanAddStudents(tenantId, 1L);
        }
        User account = existing.orElseGet(() -> newAccount(request));
        OrganizationMember member = new OrganizationMember();
        member.setUser(account);
        member.setOrganizationId(tenantId);
        member.setRoles(new HashSet<>(roles));
        // saveAndFlush: callers of IdentityService reads right after create() need the generated createdAt, e.g.
        // enrollment's roster sort key.
        return UserMapper.toResponse(memberRepository.saveAndFlush(member));
    }

    private User newAccount(CreateUserRequest request) {
        User user = new User();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setStudentCode(request.studentCode());
        user.setClassName(request.className());
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        user.setCredentialHash(passwordEncoder.encode(request.password()));
        return userRepository.saveAndFlush(user);
    }

    /**
     * A within-batch duplicate email rejects the whole request (nothing written); a row whose e-mail is already a
     * member of this organization is skipped and reported. Each row runs in its own {@link UserBulkCreateWriter}
     * (REQUIRES_NEW) transaction, so a rare concurrent-duplicate race only loses that one row.
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
                .map(email -> email.toLowerCase(Locale.ROOT))
                .toList();
        Set<String> existingEmails = emails.isEmpty()
                ? Set.of()
                : new HashSet<>(memberRepository.findActiveByOrganizationAndEmails(tenantId, emails)
                        .stream().map(member -> member.getUser().getEmail()).map(UserService::normalizeOptional)
                        .filter(email -> email != null).map(email -> email.toLowerCase(Locale.ROOT)).toList());
        long adding = rows.stream()
                .filter(row -> {
                    String email = normalizeOptional(row.email());
                    return email == null || !existingEmails.contains(email.toLowerCase(Locale.ROOT));
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
            if (email != null && existingEmails.contains(email.toLowerCase(Locale.ROOT))) {
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
        return findScoped(publicId, caller, false).toResponse();
    }

    /** Updates only the profile fields allowed by the tenant student workspace. */
    @Transactional
    public UserResponse updateStudentProfile(UUID publicId, UpdateStudentProfileRequest request,
            CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }
        OrganizationMember member = memberDirectory.requireMemberForUpdate(caller.tenantId(), publicId);
        if (!member.hasRole(Role.STUDENT) || !memberDirectory.isExclusivelyOwned(member)) {
            throw new ForbiddenUserManagementException();
        }
        User user = member.getUser();
        String email = normalizeOptional(request.email());
        if (email != null && (memberRepository.existsCanonicalEmail(caller.tenantId(),
                email.toLowerCase(Locale.ROOT), publicId)
                || userRepository.existsByEmailIgnoreCaseAndDeletedFalseAndIdNot(email, user.getId()))) {
            throw new EmailAlreadyUsedException();
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(normalizeOptional(request.phone()));
        user.setDateOfBirth(request.dateOfBirth());
        return UserMapper.toResponse(member);
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser caller) {
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        OrganizationMember member = caller.tenantId() == null ? null
                : memberDirectory.find(caller.tenantId(), caller.userId()).orElse(null);
        String organizationType = caller.tenantId() == null ? null
                : tenancyService.findOrganizationType(caller.tenantId()).orElse(null);
        return UserMapper.toResponse(user, member, organizationType);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByTenant(CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return memberRepository.findActiveByOrganization(tenantId).stream()
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
        return memberRepository.findActiveByOrganization(tenantId).stream()
                .map(member -> new UserDirectoryEntryResponse(member.getUser().getPublicId(),
                        member.getUser().getFullName()))
                .toList();
    }

    @Transactional
    public UserResponse suspend(UUID publicId, CurrentUser caller) {
        Target target = findScoped(publicId, caller, true);
        target.suspend();
        if (target.member() != null) {
            refreshTokenService.revokeForMembership(target.user().getId(), target.organizationId());
        } else {
            refreshTokenService.revokeAllForUser(target.user().getId());
        }
        return target.toResponse();
    }

    /** Reactivation is idempotent. */
    @Transactional
    public UserResponse reactivate(UUID publicId, CurrentUser caller) {
        Target target = findScoped(publicId, caller, true);
        target.reactivate();
        return target.toResponse();
    }

    @Transactional
    public UserResponse resetPassword(UUID publicId, ResetPasswordRequest request, CurrentUser caller) {
        Target target = findScoped(publicId, caller, false);
        if (!provisioningHelper.canManageTarget(caller, target.roles()) || !mayChangeCredential(caller, target)) {
            throw new ForbiddenPasswordResetException();
        }
        target.user().setCredentialHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(target.user());
        auditLogService.record(caller, IdentityConstants.USER_AGGREGATE, publicId.toString(),
                IdentityConstants.USER_PASSWORD_RESET, IdentityConstants.USER_PASSWORD_RESET_SUMMARY);
        return target.toResponse();
    }

    /**
     * Generates a fresh temporary password and queues it for delivery. The previous password is intentionally not
     * recoverable because only a hash is stored. The cleartext value is returned exactly once to the authenticated
     * operator and carried only by the in-memory notification event after the transaction commits.
     */
    @Transactional
    public GeneratedCredentialsResponse sendGeneratedCredentials(UUID publicId, CurrentUser caller) {
        Target target = findScoped(publicId, caller, false);
        if (!provisioningHelper.canManageTarget(caller, target.roles()) || !mayChangeCredential(caller, target)) {
            throw new ForbiddenPasswordResetException();
        }
        if (target.roles().contains(Role.STUDENT)) {
            throw new StudentCredentialEmailNotAllowedException();
        }
        User user = target.user();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new UserEmailRequiredException();
        }
        String temporaryPassword = rotateTemporaryPassword(user);
        eventPublisher.publishEvent(new UserCredentialsEmailRequestedEvent(
                UUID.randomUUID(), user.getPublicId(), target.organizationId(), user.getUsername(), user.getEmail(),
                user.getFullName(), temporaryPassword));
        return new GeneratedCredentialsResponse(user.getPublicId(), user.getUsername(), user.getEmail(),
                user.getFullName(), temporaryPassword, true);
    }

    /**
     * Generates a fresh Student credential for Host verification without sending email. The cleartext password is
     * returned only in this response.
     */
    @Transactional
    public GeneratedCredentialsResponse generateStudentCredentials(UUID publicId, CurrentUser caller) {
        Target target = findScoped(publicId, caller, false);
        if (!provisioningHelper.canManageTarget(caller, target.roles())
                || !target.roles().contains(Role.STUDENT) || !mayChangeCredential(caller, target)) {
            throw new ForbiddenPasswordResetException();
        }
        User user = target.user();
        String temporaryPassword = rotateTemporaryPassword(user);
        return new GeneratedCredentialsResponse(user.getPublicId(), user.getUsername(), user.getEmail(),
                user.getFullName(), temporaryPassword, false);
    }

    /**
     * The credential is global to the account. A Host may change it only for an account the organization alone
     * owns; platform operators are judged by the union of the account's roles instead.
     */
    private boolean mayChangeCredential(CurrentUser caller, Target target) {
        return caller.isPlatformUser() || target.member() != null && memberDirectory.isExclusivelyOwned(target.member());
    }

    private String rotateTemporaryPassword(User user) {
        String temporaryPassword = PasswordGenerator.generateReadable();
        user.setCredentialHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        return temporaryPassword;
    }

    /** Changes the authenticated user's password and clears the first-login flag. */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request, CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            throw new UserNotFoundException();
        }
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        if (user.getCredentialHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getCredentialHash())) {
            throw new InvalidLoginException();
        }
        user.setCredentialHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listForTenant(UUID tenantId, CurrentUser caller) {
        provisioningHelper.authorizePlatformTenantListing(caller);
        return memberRepository.findActiveByOrganization(tenantId).stream()
                .filter(member -> provisioningHelper.canManageTarget(caller, member.getRoles()))
                .map(UserMapper::toResponse)
                .toList();
    }

    /**
     * Resolves the target of a management call. A tenant caller reaches only members of its own organization; a
     * platform caller reaches any account and is judged by every role the account holds.
     */
    private Target findScoped(UUID publicId, CurrentUser caller, boolean lock) {
        Target target;
        if (caller.isPlatformUser()) {
            User user = (lock ? userRepository.findWithLockByPublicId(publicId)
                    : userRepository.findByPublicIdAndDeletedFalse(publicId))
                    .orElseThrow(UserNotFoundException::new);
            target = Target.ofAccount(user, memberRepository.findByUser_IdAndDeletedFalse(user.getId()));
        } else {
            OrganizationMember member = lock ? memberDirectory.requireMemberForUpdate(caller.tenantId(), publicId)
                    : memberDirectory.requireMember(caller.tenantId(), publicId);
            target = Target.ofMember(member);
        }
        provisioningHelper.authorizeTarget(caller, target.roles());
        return target;
    }

    /** The account a call acts on, with the roles it is judged by and the membership (if one organization). */
    private record Target(User user, OrganizationMember member, Set<Role> roles) {

        static Target ofMember(OrganizationMember member) {
            return new Target(member.getUser(), member, Set.copyOf(member.getRoles()));
        }

        static Target ofAccount(User user, List<OrganizationMember> members) {
            Set<Role> roles = new HashSet<>(user.getRoles());
            members.forEach(member -> roles.addAll(member.getRoles()));
            return new Target(user, members.size() == 1 ? members.get(0) : null, Set.copyOf(roles));
        }

        UUID organizationId() {
            return member == null ? null : member.getOrganizationId();
        }

        UserResponse toResponse() {
            return UserMapper.toResponse(user, member, null);
        }

        /** Suspension is per organization for a Host, and account-wide for a platform operator. */
        void suspend() {
            if (member != null) {
                member.suspend();
            } else {
                user.suspend();
            }
        }

        void reactivate() {
            if (member != null) {
                if (member.getStatus() == com.pte.identity.domain.UserStatus.SUSPENDED) {
                    member.reactivate();
                }
            } else if (user.isSuspended()) {
                user.reactivate();
            }
        }
    }
}
