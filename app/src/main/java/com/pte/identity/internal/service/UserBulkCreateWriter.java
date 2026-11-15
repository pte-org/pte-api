package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates one bulk-import row in its OWN transaction ({@code REQUIRES_NEW}), a separate bean since self-invocation
 * from {@link UserService} would not let Spring's proxy honor that boundary. Isolates a rare
 * concurrent-duplicate race to just this row instead of the whole batch.
 *
 * <p>Every row becomes a new Student account with a generated username: Student accounts are never shared between
 * organizations, even when the e-mail is the same.
 */
@Service
public class UserBulkCreateWriter {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserBulkCreateWriter(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record Row(String email, String fullName, String studentCode, String className,
                       String phone, LocalDate dateOfBirth) {
    }

    public record Result(User user, String generatedPassword) {
    }

    /** Empty result means the generated username lost a concurrent race on its unique key. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createGeneratedStudent(String username, Row row, UUID tenantId) {
        return persist(newStudent(username, row, tenantId, true));
    }

    private User newStudent(String username, Row row, UUID tenantId, boolean mustChangePassword) {
        User user = new User();
        user.setUsername(username);
        user.setOrganizationId(tenantId);
        user.getRoles().add(Role.STUDENT);
        user.setEmail(row.email());
        user.setFullName(row.fullName());
        user.setStudentCode(row.studentCode());
        user.setClassName(row.className());
        user.setPhone(row.phone());
        user.setDateOfBirth(row.dateOfBirth());
        user.setMustChangePassword(mustChangePassword);
        return user;
    }

    private Optional<Result> persist(User user) {
        String password = PasswordGenerator.generateReadable();
        user.setCredentialHash(passwordEncoder.encode(password));
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
        return Optional.of(new Result(saved, password));
    }
}
