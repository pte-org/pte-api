package com.pte.identity.internal.service.user;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.user.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.user.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.user.CreateUserRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.CreatedUser;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse.RowError;
import com.pte.identity.internal.dto.response.RosterRowError;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import org.springframework.dao.DataIntegrityViolationException;
import com.pte.identity.internal.exception.RosterValidationException;
import com.pte.identity.internal.exception.UserEmailRequiredException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.StudentsImportedEvent;
import com.pte.identity.internal.service.UserBulkCreateWriter;
import com.pte.identity.internal.service.UserProvisioningHelper;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.identity.internal.util.UsernameGenerator;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates organization accounts, one at a time or as a bulk roster. Every account (Student or staff) is a new
 * account of one organization with a generated username.
 */
public class UserAccountProvisioner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final UserBulkCreateWriter bulkCreateWriter;
    private final TenancyService tenancyService;
    private final ApplicationEventPublisher eventPublisher;
    private final RosterRowValidator rowValidator;

    public UserAccountProvisioner(UserRepository userRepository,
            PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
            UserBulkCreateWriter bulkCreateWriter, TenancyService tenancyService,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.bulkCreateWriter = bulkCreateWriter;
        this.tenancyService = tenancyService;
        this.eventPublisher = eventPublisher;
        this.rowValidator = new RosterRowValidator(clock);
    }

    public UserResponse create(CreateUserRequest request, CurrentUser caller) {
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());
        Set<Role> roles = provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles());
        provisioningHelper.validateTenantScope(caller, tenantId, roles);
        return createAccount(request, tenantId, roles);
    }

    /**
     * Every organization account (Student or staff) is a new account of that organization with a generated
     * username; its e-mail only has to be unique inside this organization (the same person in another organization
     * is a separate account with its own username). The account starts with a generated temporary secret that must
     * be changed at first sign-in; username and secret are e-mailed after commit.
     */
    private UserResponse createAccount(CreateUserRequest request, UUID tenantId, Set<Role> roles) {
        String email = normalizeOptional(request.email());
        if (email == null) {
            throw new UserEmailRequiredException();
        }
        if (!userRepository.findActiveByOrganizationAndEmails(tenantId,
                List.of(email.toLowerCase(Locale.ROOT))).isEmpty()) {
            throw new EmailAlreadyUsedException();
        }
        if (roles.contains(Role.STUDENT)) {
            tenancyService.assertCanAddStudents(tenantId, 1L);
        }
        String username = UsernameGenerator.generateUnused(
                tenancyService.getTenantCode(tenantId), userRepository::existsByUsername);
        String temporarySecret = PasswordGenerator.generateReadable();
        User account = newAccount(request, username, temporarySecret, tenantId, roles);
        eventPublisher.publishEvent(new UserCredentialsEmailRequestedEvent(UUID.randomUUID(),
                account.getPublicId(), tenantId, account.getUsername(), account.getEmail(), account.getFullName(),
                temporarySecret));
        return UserMapper.toResponse(account);
    }

    private User newAccount(CreateUserRequest request, String username, String temporarySecret, UUID tenantId,
            Set<Role> roles) {
        User user = new User();
        user.setUsername(username);
        user.setOrganizationId(tenantId);
        user.setRoles(new HashSet<>(roles));
        user.setEmail(normalizeOptional(request.email()));
        user.setFullName(request.fullName());
        user.setStudentCode(request.studentCode());
        user.setClassName(request.className());
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        user.setCredentialHash(passwordEncoder.encode(temporarySecret));
        user.setMustChangePassword(true);
        // saveAndFlush: callers of IdentityService reads right after create() need the generated createdAt, e.g.
        // enrollment's roster sort key.
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two simultaneous creates of the same e-mail in one organization: the unique index is the backstop.
            // (A generated-username clash is practically impossible: 31^8 values per organization code.)
            throw new EmailAlreadyUsedException();
        }
    }

    /**
     * Every row is checked first ({@link RosterRowValidator}); any format error rejects the whole roster with all
     * row errors and nothing is written. A row whose e-mail already belongs to an account of this organization is skipped and
     * reported. Every other row becomes a new Student account with a generated username, each in its own
     * {@link UserBulkCreateWriter} (REQUIRES_NEW) transaction, so a rare concurrent race only loses that one row.
     * When asked, each created Student is e-mailed their username and temporary password after commit.
     */
    public BulkCreateUsersResponse createBulk(BulkCreateUsersRequest request, CurrentUser caller) {
        provisioningHelper.authorizeBulkStudentCreation(caller);
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());
        List<BulkCreateUserRow> rows = request.rows();
        List<RosterRowError> rowErrors = rowValidator.validate(rows);
        if (!rowErrors.isEmpty()) {
            throw new RosterValidationException(rowErrors);
        }
        Set<String> existingEmails = existingMemberEmails(tenantId, rows);
        long adding = rows.stream().filter(row -> !existingEmails.contains(emailKey(row))).count();
        if (adding > 0L) {
            tenancyService.assertCanAddStudents(tenantId, adding);
        }
        String tenantCode = adding > 0L ? tenancyService.getTenantCode(tenantId) : null;
        List<CreatedUser> created = new ArrayList<>();
        List<StudentsImportedEvent.ImportedStudent> withClass = new ArrayList<>();
        List<RowError> skipped = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            BulkCreateUserRow row = rows.get(rowIndex);
            String email = normalizeOptional(row.email());
            if (existingEmails.contains(emailKey(row))) {
                skipped.add(new RowError(rowIndex, email, IdentityConstants.EMAIL_ALREADY_USED));
                continue;
            }
            UserBulkCreateWriter.Row writerRow = new UserBulkCreateWriter.Row(email,
                    normalizeOptional(row.fullName()), normalizeOptional(row.studentCode()),
                    normalizeOptional(row.className()), normalizeOptional(row.phone()), row.dateOfBirth());
            Optional<UserBulkCreateWriter.Result> result = createGeneratedStudentWithRetry(writerRow, tenantId,
                    tenantCode);
            if (result.isEmpty()) {
                skipped.add(new RowError(rowIndex, email, IdentityConstants.ROSTER_IMPORT_FAILED));
                continue;
            }
            User user = result.get().user();
            created.add(new CreatedUser(user.getPublicId(), user.getUsername(), user.getEmail(), user.getFullName(),
                    result.get().generatedPassword()));
            if (writerRow.className() != null) {
                withClass.add(new StudentsImportedEvent.ImportedStudent(user.getPublicId(), writerRow.className()));
            }
            if (request.shouldSendCredentialsEmail()) {
                eventPublisher.publishEvent(new UserCredentialsEmailRequestedEvent(UUID.randomUUID(),
                        user.getPublicId(), tenantId, user.getUsername(), user.getEmail(), user.getFullName(),
                        result.get().generatedPassword()));
            }
        }
        if (!withClass.isEmpty()) {
            // The class name is only text in identity; enrollment places the students into a matching class.
            eventPublisher.publishEvent(new StudentsImportedEvent(tenantId, withClass));
        }
        return new BulkCreateUsersResponse(created, skipped);
    }

    private Set<String> existingMemberEmails(UUID tenantId, List<BulkCreateUserRow> rows) {
        List<String> emails = rows.stream().map(UserAccountProvisioner::emailKey).toList();
        Set<String> existing = new HashSet<>();
        userRepository.findActiveByOrganizationAndEmails(tenantId, emails).stream()
                .map(user -> normalizeOptional(user.getEmail()))
                .filter(Objects::nonNull)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .forEach(existing::add);
        return existing;
    }

    /** Rows passed {@link RosterRowValidator}, so the e-mail is present. */
    private static String emailKey(BulkCreateUserRow row) {
        return normalizeOptional(row.email()).toLowerCase(Locale.ROOT);
    }

    private Optional<UserBulkCreateWriter.Result> createGeneratedStudentWithRetry(
            UserBulkCreateWriter.Row row, UUID tenantId, String tenantCode) {
        for (int attempt = 0; attempt < UsernameGenerator.COLLISION_RETRIES; attempt++) {
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
