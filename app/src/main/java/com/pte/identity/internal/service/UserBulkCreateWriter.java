package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
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
 * <p>A row with an e-mail that already belongs to an account does not create a second account: the existing
 * account gains a STUDENT membership in the organization and keeps its own credentials.
 */
@Service
public class UserBulkCreateWriter {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public UserBulkCreateWriter(UserRepository userRepository, OrganizationMemberRepository memberRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record Row(String email, String fullName, String studentCode, String className,
                       String phone, LocalDate dateOfBirth) {
    }

    /** {@code generatedPassword} is null when the row joined an account that already existed. */
    public record Result(User user, String generatedPassword) {
    }

    /** Empty result means the row lost a concurrent race on a unique key or is already a member. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createOne(Row row, UUID tenantId) {
        Optional<User> existing = userRepository.findByUsernameAndDeletedFalse(row.email());
        if (existing.isPresent()) {
            return joinExisting(existing.get(), tenantId);
        }
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(row.email())) {
            return Optional.empty();
        }
        User user = newStudent(row.email(), row, false);
        return persist(user, tenantId);
    }

    /** Creates an import-only student with a generated username and first-login password change flag. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createGeneratedStudent(String username, UUID tenantId) {
        return createGeneratedStudent(username, new Row(null, null, null, null, null, null), tenantId);
    }

    /** Creates an import-only student while preserving any optional roster profile fields. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Result> createGeneratedStudent(String username, Row row, UUID tenantId) {
        return persist(newStudent(username, row, true), tenantId);
    }

    private User newStudent(String username, Row row, boolean mustChangePassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(row.email());
        user.setFullName(row.fullName());
        user.setStudentCode(row.studentCode());
        user.setClassName(row.className());
        user.setPhone(row.phone());
        user.setDateOfBirth(row.dateOfBirth());
        user.setMustChangePassword(mustChangePassword);
        return user;
    }

    private Optional<Result> joinExisting(User existing, UUID tenantId) {
        if (existing.hasPlatformRole() || memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(existing.getId(), tenantId).isPresent()) {
            return Optional.empty();
        }
        try {
            addStudentMember(existing, tenantId);
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
        return Optional.of(new Result(existing, null));
    }

    private Optional<Result> persist(User user, UUID tenantId) {
        String password = PasswordGenerator.generateReadable();
        user.setCredentialHash(passwordEncoder.encode(password));
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
            addStudentMember(saved, tenantId);
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
        return Optional.of(new Result(saved, password));
    }

    private void addStudentMember(User user, UUID tenantId) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(tenantId);
        member.getRoles().add(Role.STUDENT);
        memberRepository.saveAndFlush(member);
    }
}
