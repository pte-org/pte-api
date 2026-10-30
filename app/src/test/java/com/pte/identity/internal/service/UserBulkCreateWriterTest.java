package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBulkCreateWriterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserBulkCreateWriter writer;

    @BeforeEach
    void setUp() {
        writer = new UserBulkCreateWriter(userRepository, memberRepository, passwordEncoder);
    }

    private UserBulkCreateWriter.Row row() {
        return new UserBulkCreateWriter.Row("student@tenant.example", "Student One", "SC-001", "12A1",
                "0900000000", LocalDate.of(2008, 1, 1));
    }

    private void stubSaveAssigningIds() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });
    }

    @Test
    void createOne_happyPath_savesAccountWithCredentialAndStudentMembership() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByUsernameAndDeletedFalse("student@tenant.example")).thenReturn(Optional.empty());
        stubSaveAssigningIds();

        Optional<UserBulkCreateWriter.Result> result = writer.createOne(row(), tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().user().getEmail()).isEqualTo("student@tenant.example");
        assertThat(result.get().generatedPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        assertThat(passwordEncoder.matches(result.get().generatedPassword(),
                result.get().user().getCredentialHash())).isTrue();
        ArgumentCaptor<OrganizationMember> member = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(memberRepository).saveAndFlush(member.capture());
        assertThat(member.getValue().getOrganizationId()).isEqualTo(tenantId);
        assertThat(member.getValue().getRoles()).containsExactly(Role.STUDENT);
    }

    @Test
    void createOne_concurrentDuplicateEmail_savesThrows_returnsEmpty_neverCreatesMembership() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByUsernameAndDeletedFalse("student@tenant.example")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        Optional<UserBulkCreateWriter.Result> result = writer.createOne(row(), tenantId);

        assertThat(result).isEmpty();
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOne_emailOfExistingAccount_joinsItAsStudentWithoutNewCredential() {
        UUID tenantId = UUID.randomUUID();
        User existing = new User();
        existing.setId(9L);
        existing.setPublicId(UUID.randomUUID());
        existing.setUsername("student@tenant.example");
        existing.setCredentialHash("existing-hash");
        when(userRepository.findByUsernameAndDeletedFalse("student@tenant.example"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(9L, tenantId))
                .thenReturn(Optional.empty());

        Optional<UserBulkCreateWriter.Result> result = writer.createOne(row(), tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().user()).isSameAs(existing);
        assertThat(result.get().generatedPassword()).isNull();
        assertThat(existing.getCredentialHash()).isEqualTo("existing-hash");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(memberRepository).saveAndFlush(any(OrganizationMember.class));
    }

    @Test
    void createOne_accountAlreadyMemberOfTheOrganization_returnsEmpty() {
        UUID tenantId = UUID.randomUUID();
        User existing = new User();
        existing.setId(9L);
        when(userRepository.findByUsernameAndDeletedFalse("student@tenant.example"))
                .thenReturn(Optional.of(existing));
        when(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(9L, tenantId))
                .thenReturn(Optional.of(new OrganizationMember()));

        assertThat(writer.createOne(row(), tenantId)).isEmpty();

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void createGeneratedStudent_setsGeneratedUsernameAndFirstLoginFlag() {
        UUID tenantId = UUID.randomUUID();
        stubSaveAssigningIds();

        Optional<UserBulkCreateWriter.Result> result = writer.createGeneratedStudent("school.abcdefgh", tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().user().getUsername()).isEqualTo("school.abcdefgh");
        assertThat(result.get().user().getEmail()).isNull();
        assertThat(result.get().user().isMustChangePassword()).isTrue();
        ArgumentCaptor<OrganizationMember> member = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(memberRepository).saveAndFlush(member.capture());
        assertThat(member.getValue().getOrganizationId()).isEqualTo(tenantId);
    }

    @Test
    void createGeneratedStudent_preservesOptionalRosterFields() {
        UUID tenantId = UUID.randomUUID();
        stubSaveAssigningIds();

        UserBulkCreateWriter.Row row = new UserBulkCreateWriter.Row(
                null, null, "SC-001", "12A1", "0900000000", LocalDate.of(2008, 1, 1));
        Optional<UserBulkCreateWriter.Result> result = writer.createGeneratedStudent("school.abcdefgh", row, tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().user().getUsername()).isEqualTo("school.abcdefgh");
        assertThat(result.get().user().getStudentCode()).isEqualTo("SC-001");
        assertThat(result.get().user().getClassName()).isEqualTo("12A1");
        assertThat(result.get().user().getPhone()).isEqualTo("0900000000");
        assertThat(result.get().user().getDateOfBirth()).isEqualTo(LocalDate.of(2008, 1, 1));
    }

    @Test
    void createOne_platformAccount_isNeverJoinedToAnOrganization() {
        UUID tenantId = UUID.randomUUID();
        User platformAdmin = new User();
        platformAdmin.setId(9L);
        platformAdmin.setRoles(java.util.Set.of(Role.PLATFORM_ADMIN));
        when(userRepository.findByUsernameAndDeletedFalse("student@tenant.example"))
                .thenReturn(Optional.of(platformAdmin));

        assertThat(writer.createOne(row(), tenantId)).isEmpty();

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOne_emailHeldByAnotherAccountUnderADifferentUsername_isSkipped() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByUsernameAndDeletedFalse("student@tenant.example")).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCaseAndDeletedFalse("student@tenant.example")).thenReturn(true);

        assertThat(writer.createOne(row(), tenantId)).isEmpty();

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
