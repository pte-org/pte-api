package com.pte.iam.service;

import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.LoginHash;
import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.event.UserCreatedEvent;
import com.pte.iam.messaging.outbox.OutboxWriter;
import com.pte.iam.repository.LoginHashRepository;
import com.pte.iam.repository.UserRepository;
import com.pte.iam.util.PasswordGenerator;
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
 * email race to just this row instead of the whole batch, mirroring
 * questionbank's {@code QuestionImportWriter} pattern.
 */
@Service
public class UserBulkCreateWriter {

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxWriter outboxWriter;

    public UserBulkCreateWriter(UserRepository userRepository, LoginHashRepository loginHashRepository,
                                 PasswordEncoder passwordEncoder, OutboxWriter outboxWriter) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxWriter = outboxWriter;
    }

    public record Row(String email, String fullName, String studentCode, String className,
                       String phone, LocalDate dateOfBirth) {
    }

    public record Result(User user, String generatedPassword) {
    }

    /** Empty result means the row lost a concurrent race on email uniqueness — caller reports it as skipped. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createOne(Row row, UUID tenantId) {
        User user = new User();
        user.setEmail(row.email());
        user.setFullName(row.fullName());
        user.setTenantId(tenantId);
        user.setRoles(Set.of(Role.STUDENT));
        user.setStudentCode(row.studentCode());
        user.setClassName(row.className());
        user.setPhone(row.phone());
        user.setDateOfBirth(row.dateOfBirth());

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

        outboxWriter.write(IamConstants.AGGREGATE_USER, saved.getPublicId().toString(),
                IamConstants.EVENT_USER_CREATED,
                new UserCreatedEvent(saved.getPublicId(), saved.getEmail(), tenantId,
                        saved.getRoles().stream().map(Role::name).toList()),
                tenantId);

        return Optional.of(new Result(saved, password));
    }
}
