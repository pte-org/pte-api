package com.pte.identity;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public identity boundary for the practice app. A practice user is simply an account that holds the STUDENT role
 * on its own and proves control of its e-mail; the organizations it can practise for are its STUDENT memberships.
 * An account that already exists for the e-mail (for example an imported student) is reused, never duplicated.
 */
@Service
public class PracticeIdentityService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final TokenHasher tokenHasher;

    public PracticeIdentityService(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            TokenHasher tokenHasher) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.tokenHasher = tokenHasher;
    }

    @Transactional
    public IdentityHandle getOrCreate(String normalizedEmail) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizedEmail).stream()
                .min(Comparator.comparing(User::getId))
                .orElseGet(() -> createPracticeUser(normalizedEmail));
        return toHandle(user, normalizedEmail);
    }

    @Transactional(readOnly = true)
    public Optional<IdentityHandle> findById(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .filter(user -> user.getEmail() != null)
                .map(user -> toHandle(user, user.getEmail().trim().toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * Confirms the e-mail belongs to the account and gives the account the practice (STUDENT) role of its own. This
     * grants no organization access: entitlement comes only from STUDENT memberships.
     */
    @Transactional
    public User provisionVerifiedUser(Long userId, String normalizedEmail) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new IllegalArgumentException(IdentityConstants.PRACTICE_IDENTITY_NOT_FOUND));
        if (user.getEmail() == null || !matchesEmail(user.getEmail().trim().toLowerCase(java.util.Locale.ROOT),
                normalizedEmail)) {
            throw new IllegalArgumentException(IdentityConstants.PRACTICE_IDENTITY_EMAIL_MISMATCH);
        }
        if (user.hasPlatformRole()) {
            throw new IllegalArgumentException(IdentityConstants.PRACTICE_IDENTITY_NOT_FOUND);
        }
        user.getRoles().add(Role.STUDENT);
        return userRepository.save(user);
    }

    /** Resolves the practice account and the organizations in which it is a student. */
    @Transactional(readOnly = true)
    public Optional<PracticeIdentityView> resolveForShellUser(UUID userPublicId) {
        Optional<User> user = userRepository.findByPublicIdAndDeletedFalse(userPublicId)
                .filter(candidate -> candidate.getRoles().contains(Role.STUDENT));
        if (user.isEmpty() || user.get().getEmail() == null || user.get().getEmail().isBlank()) {
            return Optional.empty();
        }
        List<PracticeMembershipView> memberships = memberRepository
                .findByUserAndRole(user.get().getId(), Role.STUDENT).stream()
                .map(this::toMembershipView)
                .toList();
        return Optional.of(new PracticeIdentityView(
                user.get().getPublicId(), user.get().getPublicId(), user.get().getStatus(), memberships));
    }

    private PracticeMembershipView toMembershipView(OrganizationMember member) {
        UserStatus status = member.isSuspended() ? UserStatus.SUSPENDED : UserStatus.ACTIVE;
        return new PracticeMembershipView(member.getUser().getPublicId(), member.getOrganizationId(),
                PracticeMembershipStatus.ACTIVE, status, member.hasRole(Role.STUDENT));
    }

    private User createPracticeUser(String normalizedEmail) {
        User user = new User();
        user.setUsername("practice." + UUID.nameUUIDFromBytes(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        user.setEmail(normalizedEmail);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.STUDENT));
        return userRepository.saveAndFlush(user);
    }

    private IdentityHandle toHandle(User user, String normalizedEmail) {
        PracticeIdentityStatus status = user.getStatus() == UserStatus.ACTIVE
                ? PracticeIdentityStatus.ACTIVE : PracticeIdentityStatus.REVOKED;
        return new IdentityHandle(user.getId(), user.getPublicId(), tokenHasher.hash(normalizedEmail), status);
    }

    private boolean matchesEmail(String left, String right) {
        return MessageDigest.isEqual(tokenHasher.hash(left).getBytes(StandardCharsets.UTF_8),
                tokenHasher.hash(right).getBytes(StandardCharsets.UTF_8));
    }

    public boolean matchesEmail(IdentityHandle identity, String normalizedEmail) {
        return identity != null && MessageDigest.isEqual(identity.emailHash().getBytes(StandardCharsets.UTF_8),
                tokenHasher.hash(normalizedEmail).getBytes(StandardCharsets.UTF_8));
    }

    public record IdentityHandle(Long internalId, UUID publicId, String emailHash, PracticeIdentityStatus status) {
    }

    public record PracticeIdentityView(UUID identityPublicId, UUID shellUserPublicId,
            UserStatus shellUserStatus, List<PracticeMembershipView> memberships) {
    }

    public record PracticeMembershipView(UUID userPublicId, UUID tenantId,
            PracticeMembershipStatus membershipStatus, UserStatus userStatus, boolean studentRole) {
        public boolean isActiveStudent() {
            return membershipStatus == PracticeMembershipStatus.ACTIVE
                    && userStatus == UserStatus.ACTIVE && studentRole;
        }
    }
}
