package com.pte.identity.internal.service;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.BulkCreateUserRow;
import com.pte.identity.internal.dto.request.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.CreateUserRequest;
import com.pte.identity.internal.dto.request.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.DuplicateEmailInBatchException;
import com.pte.identity.internal.exception.ForbiddenPasswordResetException;
import com.pte.identity.internal.exception.StudentCredentialEmailNotAllowedException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ported from {@code services/iam}'s {@code UserServiceTest}. Dropped:
 * {@code synchronizationWriter} mock and every {@code verify(synchronizationWriter, ...)} line
 * (UserService no longer writes an synchronization message — nothing to synchronize across a
 * process boundary that doesn't exist here), and the three
 * {@code me_*OrganizationType*} tests (organizationType is temporarily always
 * null until Phase 03 wires tenancy in-process — see UserService's class
 * javadoc), replaced by one test documenting that.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHashRepository loginHashRepository;

    @Mock
    private UserProvisioningHelper provisioningHelper;

    @Mock
    private UserBulkCreateWriter bulkCreateWriter;

    @Mock
    private TenancyService tenancyService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, loginHashRepository, passwordEncoder,
                provisioningHelper, bulkCreateWriter, tenancyService, eventPublisher);
    }

    private User userWithId(Long id, UUID publicId, UUID tenantId) {
        User user = new User();
        user.setId(id);
        user.setPublicId(publicId);
        user.setEmail("host@tenant.example");
        user.setFullName("Host Admin");
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.HOST_ADMIN));
        return user;
    }

    @Test
    void me_returnsOrganizationType_fromTenancyModule() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = userWithId(1L, userPublicId, tenantId);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(tenancyService.findOrganizationType(tenantId)).thenReturn(Optional.of("SCHOOL"));

        CurrentUser caller = new CurrentUser(userPublicId, tenantId, List.of("HOST_ADMIN"));
        UserResponse response = userService.me(caller);

        assertThat(response.organizationType()).isEqualTo("SCHOOL");
    }

    @Test
    void suspendLocksTheUserAggregateBeforeChangingExaminerEligibility() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User examiner = userWithId(1L, userPublicId, tenantId);
        examiner.setRoles(Set.of(Role.EXAMINER));
        when(userRepository.findWithLockByPublicIdAndTenantId(userPublicId, tenantId))
                .thenReturn(Optional.of(examiner));

        UserResponse response = userService.suspend(userPublicId,
                new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN")));

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(userRepository).findWithLockByPublicIdAndTenantId(userPublicId, tenantId);
    }

    @Test
    void reactivateAlsoLocksTheUserAggregateBeforeChangingExaminerEligibility() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User examiner = userWithId(1L, userPublicId, tenantId);
        examiner.setRoles(Set.of(Role.EXAMINER));
        examiner.suspend();
        when(userRepository.findWithLockByPublicIdAndTenantId(userPublicId, tenantId))
                .thenReturn(Optional.of(examiner));

        UserResponse response = userService.reactivate(userPublicId,
                new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN")));

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(userRepository).findWithLockByPublicIdAndTenantId(userPublicId, tenantId);
    }

    @Test
    void resetPassword_overwritesHash_soOldPasswordNoLongerMatchesAndNewOneDoes() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        User user = userWithId(1L, userPublicId, tenantId);

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(callerId, null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("OldPassword123", loginHash.getHash())).isFalse();
        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
        verify(loginHashRepository, times(1)).save(loginHash);
    }

    @Test
    void resetPassword_userNotFound_throwsAndNeverTouchesLoginHash() {
        UUID userPublicId = UUID.randomUUID();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() ->
                userService.resetPassword(userPublicId, new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(loginHashRepository, never()).save(any());
    }

    @Test
    void listForTenant_returnsOnlyThatTenantsUsers() {
        UUID tenantId = UUID.randomUUID();
        User user = userWithId(1L, UUID.randomUUID(), tenantId);
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(user));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, user.getRoles())).thenReturn(true);

        List<UserResponse> result = userService.listForTenant(tenantId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void listForTenant_noUsers_returnsEmptyList() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        assertThat(userService.listForTenant(tenantId, caller)).isEmpty();
    }

    @Test
    void create_roundTripsProfileFields() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One", "Password123",
                List.of("STUDENT"), null,
                "SC-001", "12A1", "0900000000", LocalDate.of(2008, 5, 1));

        when(userRepository.existsByUsernameAndTenantId(request.email(), tenantId)).thenReturn(false);
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        UserResponse response = userService.create(request, caller);

        verify(tenancyService).assertCanAddStudents(tenantId, 1L);
        assertThat(response.studentCode()).isEqualTo("SC-001");
        assertThat(response.className()).isEqualTo("12A1");
        assertThat(response.phone()).isEqualTo("0900000000");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(2008, 5, 1));
    }

    private BulkCreateUserRow row(String email) {
        return new BulkCreateUserRow(email, "Student " + email, "SC-" + email, "12A1", "0900000000",
                LocalDate.of(2008, 1, 1));
    }

    private UserBulkCreateWriter.Result writerResult(String email, UUID publicId, String password) {
        User user = new User();
        user.setPublicId(publicId);
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Student " + email);
        return new UserBulkCreateWriter.Result(user, password);
    }

    @Test
    void createBulk_createsAllRowsWhenNoneConflict() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow rowA = row("a@tenant.example");
        BulkCreateUserRow rowB = row("b@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA, rowB), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findByTenantIdAndEmailIn(tenantId, List.of(rowA.email(), rowB.email())))
                .thenReturn(List.of());
        when(bulkCreateWriter.createOne(any(), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(rowA.email(), UUID.randomUUID(), "Abcd-2345")))
                .thenReturn(Optional.of(writerResult(rowB.email(), UUID.randomUUID(), "Efgh-6789")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(2);
        assertThat(response.skipped()).isEmpty();
        assertThat(response.created().get(0).generatedPassword()).matches("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$");
        verify(tenancyService).assertCanAddStudents(tenantId, 2L);
    }

    @Test
    void createBulk_allowsStudentRowWithoutEmailOrFullName_andReturnsGeneratedUsername() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow row = new BulkCreateUserRow(null, null, null, null, null, null);
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(row), null);
        UUID publicId = UUID.randomUUID();
        User createdUser = new User();
        createdUser.setPublicId(publicId);
        createdUser.setUsername("school.abcd2345");

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), any(UserBulkCreateWriter.Row.class), eq(tenantId)))
                .thenReturn(Optional.of(new UserBulkCreateWriter.Result(createdUser, "Abcd-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).username()).isEqualTo("school.abcd2345");
        assertThat(response.created().get(0).email()).isNull();
        assertThat(response.created().get(0).fullName()).isNull();
        assertThat(response.skipped()).isEmpty();
        verify(bulkCreateWriter, never()).createOne(any(), any());
        verify(tenancyService).assertCanAddStudents(tenantId, 1L);
    }

    @Test
    void createBulk_skipsExistingEmailRow_butStillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow existing = row("existing@tenant.example");
        BulkCreateUserRow fresh = row("fresh@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(existing, fresh), null);

        User existingUser = new User();
        existingUser.setEmail(existing.email());

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findByTenantIdAndEmailIn(tenantId, List.of(existing.email(), fresh.email())))
                .thenReturn(List.of(existingUser));
        when(bulkCreateWriter.createOne(any(), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(fresh.email(), UUID.randomUUID(), "Ijkl-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).email()).isEqualTo(fresh.email());
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().get(0).rowIndex()).isEqualTo(0);
        assertThat(response.skipped().get(0).email()).isEqualTo(existing.email());
        verify(tenancyService).assertCanAddStudents(tenantId, 1L);
    }

    @Test
    void createBulk_duplicateEmailWithinBatch_rejectsWholeBatch_noWrites() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
        BulkCreateUserRow rowA = row("dup@tenant.example");
        BulkCreateUserRow rowB = row("dup@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA, rowB), null);

        when(provisioningHelper.resolveTargetTenant(any(), any())).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> userService.createBulk(request, caller))
                .isInstanceOf(DuplicateEmailInBatchException.class);

        verify(bulkCreateWriter, never()).createOne(any(), any());
    }

    @Test
    void createBulk_writerLosesConcurrentRace_skipsThatRowOnly_stillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow raced = row("raced@tenant.example");
        BulkCreateUserRow fine = row("fine@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(raced, fine), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findByTenantIdAndEmailIn(tenantId, List.of(raced.email(), fine.email())))
                .thenReturn(List.of());
        when(bulkCreateWriter.createOne(any(), eq(tenantId)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(writerResult(fine.email(), UUID.randomUUID(), "Mnop-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).email()).isEqualTo(fine.email());
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().get(0).email()).isEqualTo(raced.email());
        verify(tenancyService).assertCanAddStudents(tenantId, 2L);
    }

    @Test
    void create_studentLimitExceeded_rejectsBeforeInsert() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One", "Password123",
                List.of("STUDENT"), null, null, null, null, null);

        when(userRepository.existsByUsernameAndTenantId(request.email(), tenantId)).thenReturn(false);
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        doThrow(new StudentLimitExceededException(100L, 100L, 1L))
                .when(tenancyService).assertCanAddStudents(tenantId, 1L);

        assertThatThrownBy(() -> userService.create(request, caller))
                .isInstanceOf(StudentLimitExceededException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(loginHashRepository, never()).save(any());
    }

    @Test
    void changeOwnPassword_updatesHashAndClearsFirstLoginFlag() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = userWithId(1L, userPublicId, tenantId);
        student.setMustChangePassword(true);
        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("Temporary123"));
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(student));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        userService.changeOwnPassword(new ChangePasswordRequest("Temporary123", "NewPassword456"),
                new CurrentUser(userPublicId, tenantId, List.of("STUDENT")));

        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
        assertThat(student.isMustChangePassword()).isFalse();
        verify(userRepository).save(student);
        verify(loginHashRepository).save(loginHash);
    }

    @Test
    void resetPassword_hostAdmin_succeedsAgainstSameTenantStudent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = userWithId(1L, userPublicId, tenantId);
        student.setRoles(Set.of(Role.STUDENT));

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(student));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
    }

    @Test
    void sendGeneratedCredentials_rotatesHash_marksFirstLogin_andPublishesEmailEvent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User examStaff = userWithId(1L, userPublicId, tenantId);
        examStaff.setUsername("tenant.proctor");
        examStaff.setEmail("proctor@tenant.example");
        examStaff.setRoles(Set.of(Role.PROCTOR));

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(examStaff));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, examStaff.getRoles())).thenReturn(true);

        GeneratedCredentialsResponse response = userService.sendGeneratedCredentials(userPublicId, caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(response.username()).isEqualTo("tenant.proctor");
        assertThat(response.email()).isEqualTo("proctor@tenant.example");
        assertThat(response.temporaryPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(response.emailQueued()).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123", loginHash.getHash())).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), loginHash.getHash())).isTrue();
        assertThat(examStaff.isMustChangePassword()).isTrue();
        verify(eventPublisher).publishEvent(any(UserCredentialsEmailRequestedEvent.class));
        verify(loginHashRepository).save(loginHash);
        verify(userRepository).save(examStaff);
    }

    @Test
    void sendGeneratedCredentials_withoutEmail_rejectsBeforeHashChange() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User examStaff = userWithId(1L, userPublicId, tenantId);
        examStaff.setUsername("tenant.proctor");
        examStaff.setEmail(null);
        examStaff.setRoles(Set.of(Role.PROCTOR));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(examStaff));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, examStaff.getRoles())).thenReturn(true);

        assertThatThrownBy(() -> userService.sendGeneratedCredentials(userPublicId, caller))
                .isInstanceOf(com.pte.identity.internal.exception.UserEmailRequiredException.class);

        verify(loginHashRepository, never()).findByUserId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sendGeneratedCredentials_student_isRejectedBeforeHashChange() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = userWithId(1L, userPublicId, tenantId);
        student.setUsername("tenant.student");
        student.setEmail("student@tenant.example");
        student.setRoles(Set.of(Role.STUDENT));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(student));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);

        assertThatThrownBy(() -> userService.sendGeneratedCredentials(userPublicId, caller))
                .isInstanceOf(StudentCredentialEmailNotAllowedException.class);

        verify(loginHashRepository, never()).findByUserId(any());
        verify(loginHashRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void generateStudentCredentials_rotatesHash_withoutPublishingEmailEvent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = userWithId(1L, userPublicId, tenantId);
        student.setUsername("tenant.student");
        student.setEmail(null);
        student.setRoles(Set.of(Role.STUDENT));

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(student));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(true);

        GeneratedCredentialsResponse response = userService.generateStudentCredentials(userPublicId, caller);

        assertThat(response.username()).isEqualTo("tenant.student");
        assertThat(response.email()).isNull();
        assertThat(response.temporaryPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(response.emailQueued()).isFalse();
        assertThat(passwordEncoder.matches("OldPassword123", loginHash.getHash())).isFalse();
        assertThat(passwordEncoder.matches(response.temporaryPassword(), loginHash.getHash())).isTrue();
        assertThat(student.isMustChangePassword()).isTrue();
        verify(loginHashRepository).save(loginHash);
        verify(userRepository).save(student);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetPassword_hostAdmin_againstSameTenantHostAdmin_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User fellowHostAdmin = userWithId(1L, userPublicId, tenantId);
        fellowHostAdmin.setRoles(Set.of(Role.HOST_ADMIN));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(fellowHostAdmin));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, fellowHostAdmin.getRoles())).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(loginHashRepository, never()).save(any());
    }

    @Test
    void resetPassword_hostAdmin_againstDifferentTenant_throwsUserNotFound() {
        UUID userPublicId = UUID.randomUUID();
        UUID callerTenantId = UUID.randomUUID();

        when(userRepository.findByPublicIdAndTenantId(userPublicId, callerTenantId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(loginHashRepository, never()).save(any());
    }

    @Test
    void resetPassword_platformAdmin_stillWorksAgainstHostAdminAnyTenant() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User hostAdmin = userWithId(1L, userPublicId, tenantId);
        hostAdmin.setRoles(Set.of(Role.HOST_ADMIN));

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(hostAdmin));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, hostAdmin.getRoles())).thenReturn(true);
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
    }

    @Test
    void resetPassword_platformAdmin_againstLowerTenantRole_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = userWithId(1L, userPublicId, tenantId);
        student.setRoles(Set.of(Role.STUDENT));

        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(student));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(provisioningHelper.canManageTarget(caller, student.getRoles())).thenReturn(false);

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(loginHashRepository, never()).save(any());
    }
}
