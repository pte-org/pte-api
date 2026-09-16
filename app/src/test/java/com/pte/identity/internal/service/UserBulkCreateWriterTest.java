package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private LoginHashRepository loginHashRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserBulkCreateWriter writer;

    @BeforeEach
    void setUp() {
        writer = new UserBulkCreateWriter(userRepository, loginHashRepository, passwordEncoder);
    }

    private UserBulkCreateWriter.Row row() {
        return new UserBulkCreateWriter.Row("student@tenant.example", "Student One", "SC-001", "12A1",
                "0900000000", LocalDate.of(2008, 1, 1));
    }

    @Test
    void createOne_happyPath_savesUserAndLoginHash() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        Optional<UserBulkCreateWriter.Result> result = writer.createOne(row(), tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().user().getEmail()).isEqualTo("student@tenant.example");
        assertThat(result.get().generatedPassword()).matches("^[2-9A-HJ-NP-Za-hj-np-z]{4}-[2-9A-HJ-NP-Za-hj-np-z]{4}$");
        verify(loginHashRepository).save(any());
    }

    @Test
    void createOne_concurrentDuplicateEmail_savesThrows_returnsEmpty_neverTouchesLoginHash() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("unique violation"));

        Optional<UserBulkCreateWriter.Result> result = writer.createOne(row(), tenantId);

        assertThat(result).isEmpty();
        verify(loginHashRepository, never()).save(any());
    }

    @Test
    void createGeneratedStudent_setsGeneratedUsernameAndFirstLoginFlag() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        Optional<UserBulkCreateWriter.Result> result = writer.createGeneratedStudent("school.abcdefgh", tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().user().getUsername()).isEqualTo("school.abcdefgh");
        assertThat(result.get().user().getEmail()).isNull();
        assertThat(result.get().user().isMustChangePassword()).isTrue();
        assertThat(result.get().user().getTenantId()).isEqualTo(tenantId);
        verify(loginHashRepository).save(any());
    }
}
