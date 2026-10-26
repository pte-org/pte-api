package com.pte.identity.internal.service.user;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.user.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.user.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.user.CreateUserRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.CreatedUser;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.RowError;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.DuplicateEmailInBatchException;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.service.UserBulkCreateWriter;
import com.pte.identity.internal.service.UserProvisioningHelper;
import com.pte.identity.internal.util.UsernameGenerator;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates organization memberships, one at a time or as a bulk roster. An e-mail that already belongs to an account
 * joins that account to the organization instead of creating a second account.
 */
public class UserAccountProvisioner {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final UserBulkCreateWriter bulkCreateWriter;
    private final TenancyService tenancyService;

    public UserAccountProvisioner(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
            UserBulkCreateWriter bulkCreateWriter, TenancyService tenancyService) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.bulkCreateWriter = bulkCreateWriter;
        this.tenancyService = tenancyService;
    }

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
                .map(UserAccountProvisioner::normalizeOptional)
                .filter(email -> email != null)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .toList();
        Set<String> existingEmails = emails.isEmpty()
                ? Set.of()
                : new HashSet<>(memberRepository.findActiveByOrganizationAndEmails(tenantId, emails)
                        .stream().map(member -> member.getUser().getEmail())
                        .map(UserAccountProvisioner::normalizeOptional)
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

    /** Trims an optional input field; blank becomes {@code null}. */
    public static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
