package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates one bulk-import row in its OWN transaction ({@code REQUIRES_NEW}) —
 * a separate bean since self-invocation from {@link UserService} wouldn't let
 * Spring's proxy honor that boundary. Isolates a rare concurrent-duplicate-
 * email race to just this row instead of the whole batch.
 */
@Service
public class UserBulkCreateWriter {

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;

    public UserBulkCreateWriter(UserRepository userRepository, LoginHashRepository loginHashRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record Row(String email, String fullName, String studentCode, String className,
                       String phone, LocalDate dateOfBirth) {
    }

    public record Result(User user, String generatedPassword) {
    }

    /** Empty result means the row lost a concurrent race on a unique key — caller reports it as skipped. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createOne(Row row, UUID tenantId) {
        User user = new User();
        // username = email here too (plans/quang-tenant-commercialization
        // Phase 1) — this bulk-create path predates Phase 8's roster import
        // and per-tenant student username generation; it still needs SOME
        // value for the now-NOT-NULL username column. The tenant-scoped
        // username collision surfaces as the DataIntegrityViolationException
        // already caught below, same as an email collision did before.
        user.setUsername(row.email());
        user.setEmail(row.email());
        user.setFullName(row.fullName());
        user.setTenantId(tenantId);
        user.setRoles(Set.of(Role.STUDENT));
        user.setStudentCode(row.studentCode());
        user.setClassName(row.className());
        user.setPhone(row.phone());
        user.setDateOfBirth(row.dateOfBirth());

        return persist(user);
    }

    /** Creates an import-only student with a generated username and first-login password change flag. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createGeneratedStudent(String username, UUID tenantId) {
        User user = new User();
        user.setUsername(username);
        user.setTenantId(tenantId);
        user.setRoles(Set.of(Role.STUDENT));
        user.setMustChangePassword(true);

        return persist(user);
    }

    private Optional<Result> persist(User user) {
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }

        String password = PasswordGenerator.generateReadable();
        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(saved.getId());
        loginHash.setHash(passwordEncoder.encode(password));
        loginHashRepository.save(loginHash);

        return Optional.of(new Result(saved, password));
    }
}
