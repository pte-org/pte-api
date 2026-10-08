package com.pte.identity;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.domain.PracticeIdentity;
import com.pte.identity.internal.domain.PracticeIdentityMembership;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.repository.PracticeIdentityMembershipRepository;
import com.pte.identity.internal.repository.PracticeIdentityRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.security.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public identity boundary for the practice app. It owns the mapping between
 * a verified global email identity and canonical imported student rows.
 */
@Service
public class PracticeIdentityService {

    private final PracticeIdentityRepository identityRepository;
    private final PracticeIdentityMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TokenHasher tokenHasher;

    public PracticeIdentityService(PracticeIdentityRepository identityRepository,
            PracticeIdentityMembershipRepository membershipRepository, UserRepository userRepository,
            TokenHasher tokenHasher) {
        this.identityRepository = identityRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
    }

    @Transactional
    public IdentityHandle getOrCreate(String normalizedEmail) {
        String emailHash = tokenHasher.hash(normalizedEmail);
        PracticeIdentity identity = identityRepository.findByNormalizedEmailHashAndDeletedFalse(emailHash)
                .orElseGet(() -> {
                    PracticeIdentity created = new PracticeIdentity();
                    created.setNormalizedEmailHash(emailHash);
                    return identityRepository.save(created);
                });
        return toHandle(identity);
    }

    @Transactional(readOnly = true)
    public Optional<IdentityHandle> findById(Long identityId) {
        return identityRepository.findByIdAndDeletedFalse(identityId).map(this::toHandle);
    }

    /**
     * Marks an email identity verified and creates only a tenantless shell user.
     * The shell user is not a tenant membership and has no entitlement by itself.
     */
    @Transactional
    public User provisionVerifiedUser(Long identityId, String normalizedEmail) {
        PracticeIdentity identity = identityRepository.findWithLockByIdAndDeletedFalse(identityId)
                .orElseThrow(() -> new IllegalArgumentException(IdentityConstants.PRACTICE_IDENTITY_NOT_FOUND));
        if (!matchesEmail(identity.getNormalizedEmailHash(), normalizedEmail)) {
            throw new IllegalArgumentException(IdentityConstants.PRACTICE_IDENTITY_EMAIL_MISMATCH);
        }
        User shellUser = identity.getShellUserId() == null
                ? createShellUser(normalizedEmail)
                : userRepository.findById(identity.getShellUserId())
                        .orElseGet(() -> createShellUser(normalizedEmail));
        shellUser.setEmail(normalizedEmail);
        identity.setShellUserId(shellUser.getId());
        identity.setVerifiedAt(Instant.now());
        identityRepository.save(identity);
        syncMemberships(identity, normalizedEmail);
        return shellUser;
    }

    /** Resolves and refreshes canonical imported memberships for a shell user. */
    @Transactional
    public Optional<PracticeIdentityView> resolveForShellUser(UUID shellUserPublicId) {
        Optional<User> shellUser = userRepository.findByPublicId(shellUserPublicId)
                .filter(user -> !user.isDeleted());
        if (shellUser.isEmpty() || shellUser.get().getTenantId() != null) {
            return Optional.empty();
        }
        Optional<PracticeIdentity> identity = identityRepository.findByShellUserIdAndDeletedFalse(
                shellUser.get().getId());
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        if (identity.get().getStatus() != PracticeIdentityStatus.ACTIVE) {
            return Optional.empty();
        }
        String shellEmail = shellUser.get().getEmail();
        if (shellEmail == null || shellEmail.isBlank()) {
            return Optional.empty();
        }
        shellEmail = shellEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (!matchesEmail(identity.get().getNormalizedEmailHash(), shellEmail)) {
            return Optional.empty();
        }
        syncMemberships(identity.get(), shellEmail);
        return Optional.of(toView(identity.get(), shellUser.get()));
    }

    private void syncMemberships(PracticeIdentity identity, String normalizedEmail) {
        List<User> importedStudents = userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizedEmail)
                .stream()
                .filter(user -> user.getTenantId() != null && user.getRoles().contains(Role.STUDENT))
                .toList();
        Map<UUID, PracticeIdentityMembership> existingMemberships = membershipRepository
                .findByIdentityIdAndDeletedFalse(identity.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PracticeIdentityMembership::getUserPublicId, membership -> membership));
        Map<UUID, User> importedByPublicId = new HashMap<>();
        for (User student : importedStudents) {
            importedByPublicId.put(student.getPublicId(), student);
            PracticeIdentityMembership membership = existingMemberships.computeIfAbsent(student.getPublicId(), key -> {
                        PracticeIdentityMembership created = new PracticeIdentityMembership();
                        created.setIdentityId(identity.getId());
                        created.setUserPublicId(student.getPublicId());
                        created.setLinkedAt(Instant.now());
                        return created;
                    });
            membership.setTenantId(student.getTenantId());
            membership.setStatus(PracticeMembershipStatus.ACTIVE);
            membershipRepository.save(membership);
        }

        for (PracticeIdentityMembership membership : membershipRepository
                .findByIdentityIdAndDeletedFalse(identity.getId())) {
            if (!importedByPublicId.containsKey(membership.getUserPublicId())) {
                membership.setStatus(PracticeMembershipStatus.REMOVED);
                membershipRepository.save(membership);
            }
        }
    }

    private User createShellUser(String normalizedEmail) {
        User shellUser = new User();
        shellUser.setUsername("practice." + UUID.nameUUIDFromBytes(
                normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        shellUser.setEmail(normalizedEmail);
        shellUser.setTenantId(null);
        shellUser.setStatus(UserStatus.ACTIVE);
        shellUser.setRoles(Set.of(Role.STUDENT));
        return userRepository.saveAndFlush(shellUser);
    }

    private PracticeIdentityView toView(PracticeIdentity identity, User shellUser) {
        Set<UUID> userIds = new HashSet<>();
        List<PracticeIdentityMembership> memberships = membershipRepository
                .findByIdentityIdAndDeletedFalse(identity.getId());
        memberships.forEach(membership -> userIds.add(membership.getUserPublicId()));
        // Memberships may span multiple tenants; batch-load canonical users by
        // stable public IDs without inferring ownership from email alone.
        Map<UUID, User> usersById = userRepository.findByPublicIdInAndDeletedFalse(List.copyOf(userIds)).stream()
                .collect(java.util.stream.Collectors.toMap(User::getPublicId, user -> user));
        List<PracticeMembershipView> membershipViews = memberships.stream()
                .map(membership -> {
                    User user = usersById.get(membership.getUserPublicId());
                    return new PracticeMembershipView(
                            membership.getUserPublicId(), membership.getTenantId(), membership.getStatus(),
                            user == null ? null : user.getStatus(), user != null && user.getRoles().contains(Role.STUDENT));
                })
                .toList();
        return new PracticeIdentityView(
                identity.getPublicId(), shellUser.getPublicId(), shellUser.getStatus(), membershipViews);
    }

    private IdentityHandle toHandle(PracticeIdentity identity) {
        return new IdentityHandle(identity.getId(), identity.getPublicId(), identity.getNormalizedEmailHash(),
                identity.getStatus());
    }

    private boolean matchesEmail(String emailHash, String normalizedEmail) {
        return MessageDigest.isEqual(emailHash.getBytes(StandardCharsets.UTF_8),
                tokenHasher.hash(normalizedEmail).getBytes(StandardCharsets.UTF_8));
    }

    public boolean matchesEmail(IdentityHandle identity, String normalizedEmail) {
        return identity != null && matchesEmail(identity.emailHash(), normalizedEmail);
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
