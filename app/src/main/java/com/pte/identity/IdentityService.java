package com.pte.identity;

import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.shared.exception.DomainException;
import com.pte.tenancy.StudentCountProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only door other modules use to reach {@code identity}. The repositories stay in {@code internal/}: a module
 * reaching straight into another module's repository is exactly the shortcut the module boundaries forbid.
 *
 * <p>Every organization-scoped read goes through {@link OrganizationMember}: an account is visible to an
 * organization only while it holds an active membership there.
 */
@Service
public class IdentityService implements StudentCountProvider {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findById(UUID publicId) {
        return userRepository.findByPublicId(publicId);
    }

    /** Tenant-scoped student read used by the tenant student workspace. */
    @Transactional(readOnly = true)
    public Optional<StudentIdentityView> findStudentByTenant(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            return Optional.empty();
        }
        return memberRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.hasRole(Role.STUDENT))
                .map(StudentIdentityView::from);
    }

    public boolean existsById(UUID publicId) {
        return userRepository.findByPublicId(publicId).isPresent();
    }

    /** Whether the account holds an active membership in the organization (any role). */
    @Transactional(readOnly = true)
    public boolean isMemberOf(UUID userPublicId, UUID organizationId) {
        return userPublicId != null && organizationId != null
                && memberRepository.findActiveByUserAndOrganization(userPublicId, organizationId).isPresent();
    }

    /**
     * Re-authenticates the caller by password alone (no new token issued) for a step-up confirmation gate in front
     * of a sensitive read, e.g. billing's "reveal license key" action. Returns {@code false} for any unknown user
     * or wrong password rather than throwing, so the caller can map it to its own domain-appropriate error.
     */
    public boolean verifyPassword(UUID userPublicId, String rawPassword) {
        if (userPublicId == null || rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        return userRepository.findByPublicId(userPublicId)
                .map(User::getCredentialHash)
                .map(hash -> passwordEncoder.matches(rawPassword, hash))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<User> findByTenantIdAndRole(UUID tenantId, Role role) {
        return memberRepository.findActiveByOrganizationAndRole(tenantId, role).stream()
                .map(OrganizationMember::getUser)
                .toList();
    }

    /** Returns only active, non-deleted recipients for tenant notifications. */
    @Transactional(readOnly = true)
    public List<User> findActiveByTenantIdAndRole(UUID tenantId, Role role) {
        return findByTenantIdAndRole(tenantId, role).stream()
                .filter(user -> !user.isDeleted() && user.getStatus() == UserStatus.ACTIVE)
                .toList();
    }

    /** Resolves an active, non-deleted student in exactly one tenant. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveStudentInTenant(UUID publicId, UUID tenantId) {
        return findActiveInTenant(publicId, tenantId, Role.STUDENT);
    }

    /** Resolves an active, non-deleted proctor in exactly one tenant. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveProctorInTenant(UUID publicId, UUID tenantId) {
        return findActiveInTenant(publicId, tenantId, Role.PROCTOR);
    }

    /** Resolves an active, non-deleted proctor or examiner in exactly one tenant. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveStaffInTenant(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            return Optional.empty();
        }
        return memberRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.isActive()
                        && (member.hasRole(Role.PROCTOR) || member.hasRole(Role.EXAMINER)))
                .map(OrganizationMember::getUser);
    }

    /** Returns active students in one tenant in one repository call for batch commands. */
    @Transactional(readOnly = true)
    public Set<UUID> findActiveStudentPublicIds(UUID tenantId, Collection<UUID> publicIds) {
        if (tenantId == null || publicIds == null || publicIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> targetIds = publicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return memberRepository.findActiveByUsersAndOrganization(targetIds, tenantId).stream()
                .filter(OrganizationMember::isActive)
                .filter(member -> member.hasRole(Role.STUDENT))
                .map(member -> member.getUser().getPublicId())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Fails closed when a command references a non-eligible student identity. */
    public void requireActiveStudentInTenant(UUID publicId, UUID tenantId) {
        if (findActiveStudentInTenant(publicId, tenantId).isEmpty()) {
            throw new IdentityNotEligibleException();
        }
    }

    /** Fails closed for a batch when any referenced student is not eligible. */
    public void requireActiveStudentsInTenant(Collection<UUID> publicIds, UUID tenantId) {
        if (publicIds == null || publicIds.isEmpty()) {
            throw new IdentityNotEligibleException();
        }
        List<UUID> requestedIds = publicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (publicIds.stream().anyMatch(Objects::isNull)
                || findActiveStudentPublicIds(tenantId, requestedIds).size() != requestedIds.size()) {
            throw new IdentityNotEligibleException();
        }
    }

    /** Fails closed when a command references a non-eligible proctor identity. */
    public void requireActiveProctorInTenant(UUID publicId, UUID tenantId) {
        if (findActiveProctorInTenant(publicId, tenantId).isEmpty()) {
            throw new IdentityNotEligibleException();
        }
    }

    /** Returns active, nondeleted members for an owned role fan-out contract. */
    @Transactional(readOnly = true)
    public List<IdentityRoleMember> findActiveRoleMembers(Role role) {
        if (role == null) {
            return List.of();
        }
        return memberRepository.findActiveByRole(role, UserStatus.ACTIVE).stream()
                .map(member -> new IdentityRoleMember(member.getUser().getPublicId(), member.getOrganizationId()))
                .toList();
    }

    /** Returns active, nondeleted members of one role in one tenant. */
    @Transactional(readOnly = true)
    public List<IdentityRoleMember> findActiveRoleMembers(UUID tenantId, Role role) {
        if (tenantId == null || role == null) {
            return List.of();
        }
        return memberRepository.findActiveByOrganizationAndRole(tenantId, role).stream()
                .filter(OrganizationMember::isActive)
                .map(member -> new IdentityRoleMember(member.getUser().getPublicId(), member.getOrganizationId()))
                .toList();
    }

    /**
     * Holds the existing identity row serialization while a role-owned write commits. A null tenant is the exact
     * platform scope: the account's own roles; otherwise the membership in that tenant must carry the role.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveRoleMember(UUID publicId, UUID tenantId, Role role) {
        if (publicId == null || role == null) {
            return false;
        }
        if (tenantId == null) {
            return userRepository.findWithLockByPublicId(publicId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getRoles().contains(role))
                    .isPresent();
        }
        return memberRepository.findWithLockByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.isActive() && member.hasRole(role))
                .isPresent();
    }

    /**
     * Locks every requested active student identity in deterministic order for the duration of the caller
     * transaction. Enrollment uses this as the serialization boundary before reading slot conflicts, checking
     * capacity, and inserting rows.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActiveStudentsInTenant(Collection<UUID> publicIds, UUID tenantId) {
        if (tenantId == null || publicIds == null || publicIds.isEmpty()
                || publicIds.stream().anyMatch(Objects::isNull)) {
            throw new IdentityNotEligibleException();
        }
        List<UUID> orderedIds = publicIds.stream().distinct().sorted().toList();
        List<OrganizationMember> locked = memberRepository.findWithLockByUsersAndOrganization(orderedIds, tenantId);
        Set<UUID> lockedIds = locked.stream().map(member -> member.getUser().getPublicId())
                .collect(Collectors.toSet());
        if (locked.size() != orderedIds.size()
                || !lockedIds.equals(Set.copyOf(orderedIds))
                || locked.stream().anyMatch(member -> !member.isActive()
                        || !member.hasRole(Role.STUDENT))) {
            throw new IdentityNotEligibleException();
        }
    }

    /** Returns only active EXAMINER identities in the supplied tenant; invalid IDs are omitted. */
    @Transactional(readOnly = true)
    public List<ExaminerIdentityView> findActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        if (tenantId == null || examinerPublicIds == null || examinerPublicIds.isEmpty()) {
            return List.of();
        }
        return memberRepository.findActiveByUsersAndOrganization(examinerPublicIds, tenantId).stream()
                .filter(member -> member.isActive() && member.hasRole(Role.EXAMINER))
                .map(this::toExaminerView)
                .toList();
    }

    /** Display names of the tenant's users, keyed by public id; unknown or deleted ids are omitted. */
    @Transactional(readOnly = true)
    public Map<UUID, String> findFullNames(UUID tenantId, Collection<UUID> userPublicIds) {
        if (tenantId == null || userPublicIds == null || userPublicIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findActiveByUsersAndOrganization(List.copyOf(userPublicIds), tenantId).stream()
                .collect(Collectors.toMap(member -> member.getUser().getPublicId(),
                        member -> member.getUser().getFullName(), (existing, replacement) -> existing));
    }

    /**
     * Locks the selected tenant-owned memberships and returns only active Examiner identities. The caller must hold
     * one transaction through the protected write so suspension or role changes cannot race an assignment commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ExaminerIdentityView> lockActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        if (tenantId == null || examinerPublicIds == null || examinerPublicIds.isEmpty()) {
            return List.of();
        }
        List<UUID> orderedIds = examinerPublicIds.stream().distinct().sorted().toList();
        return memberRepository.findWithLockByUsersAndOrganization(orderedIds, tenantId).stream()
                .filter(member -> member.isActive() && member.hasRole(Role.EXAMINER))
                .map(this::toExaminerView)
                .toList();
    }

    @Override
    public long countStudents(UUID tenantId) {
        return memberRepository.countByOrganizationIdAndRole(tenantId, Role.STUDENT);
    }

    /**
     * {@code billing.TenantApplicationService.approve()}: makes the contact the first HOST_ADMIN of a newly approved
     * organization. If the e-mail already belongs to an account (for example a Host registering a second
     * organization), that account simply gains a membership and no new credentials are generated. Joins the
     * caller's transaction.
     */
    public HostAdminCreated createHostAdmin(UUID tenantId, String email) {
        Optional<User> existing = userRepository.findByUsernameAndDeletedFalse(email);
        if (existing.isPresent()) {
            if (existing.get().hasPlatformRole()) {
                throw new HostContactNotAllowedException();
            }
            addMember(existing.get(), tenantId, Role.HOST_ADMIN);
            return new HostAdminCreated(existing.get(), null);
        }
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        String password = PasswordGenerator.generateReadable();
        user.setCredentialHash(passwordEncoder.encode(password));
        User saved = userRepository.saveAndFlush(user);
        addMember(saved, tenantId, Role.HOST_ADMIN);
        return new HostAdminCreated(saved, password);
    }

    private void addMember(User user, UUID tenantId, Role role) {
        OrganizationMember member = memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(
                user.getId(), tenantId).orElseGet(OrganizationMember::new);
        member.setUser(user);
        member.setOrganizationId(tenantId);
        member.getRoles().add(role);
        memberRepository.saveAndFlush(member);
    }

    /** Returns minimal display names and email addresses for enrolled students within the tenant. */
    @Transactional(readOnly = true)
    public Map<UUID, StudentDisplayNameView> getStudentDisplayNames(UUID tenantId,
            Collection<UUID> studentPublicIds) {
        if (tenantId == null || studentPublicIds == null || studentPublicIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> targetIds = studentPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return memberRepository.findActiveByUsersAndOrganization(targetIds, tenantId).stream()
                .map(OrganizationMember::getUser)
                .collect(Collectors.toMap(
                        User::getPublicId,
                        user -> {
                            String name = (user.getFullName() != null && !user.getFullName().isBlank())
                                    ? user.getFullName()
                                    : user.getUsername();
                            return new StudentDisplayNameView(user.getPublicId(), name, user.getEmail());
                        },
                        (existing, replacement) -> existing));
    }

    /** Fresh active PROCTOR check for current tenant; ensures non-deleted and ACTIVE status. */
    @Transactional(readOnly = true)
    public boolean isActiveProctor(UUID userPublicId, UUID tenantId) {
        return findActiveProctorInTenant(userPublicId, tenantId).isPresent();
    }

    private ExaminerIdentityView toExaminerView(OrganizationMember member) {
        User user = member.getUser();
        return new ExaminerIdentityView(user.getPublicId(), user.getFullName(), user.getEmail());
    }

    private Optional<User> findActiveInTenant(UUID publicId, UUID tenantId, Role role) {
        if (publicId == null || tenantId == null || role == null) {
            return Optional.empty();
        }
        return memberRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.isActive() && member.hasRole(role))
                .map(OrganizationMember::getUser);
    }

    /** The application contact e-mail belongs to a platform operator account, which cannot administer a tenant. */
    public static final class HostContactNotAllowedException extends DomainException {
        public HostContactNotAllowedException() {
            super(HttpStatus.CONFLICT, IdentityConstants.HOST_CONTACT_IS_PLATFORM_ACCOUNT);
        }
    }

    /** Stable 404 contract for cross-tenant, wrong-role, deleted, or suspended identities. */
    public static final class IdentityNotEligibleException extends DomainException {
        public IdentityNotEligibleException() {
            super(HttpStatus.NOT_FOUND, IdentityConstants.IDENTITY_NOT_ELIGIBLE);
        }
    }
}
