package com.pte.identity;

import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.service.membership.HostAdminProvisioner;
import com.pte.identity.internal.service.membership.IdentityMemberLocks;
import com.pte.identity.internal.service.membership.IdentityMemberQuery;
import com.pte.shared.exception.DomainException;
import com.pte.tenancy.StudentCountProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final IdentityMemberQuery memberQuery;
    private final IdentityMemberLocks memberLocks;
    private final HostAdminProvisioner hostAdminProvisioner;

    public IdentityService(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberQuery = new IdentityMemberQuery(memberRepository);
        this.memberLocks = new IdentityMemberLocks(userRepository, memberRepository);
        this.hostAdminProvisioner = new HostAdminProvisioner(userRepository, memberRepository, passwordEncoder);
    }

    public Optional<User> findById(UUID publicId) {
        return userRepository.findByPublicId(publicId);
    }

    /** Tenant-scoped student read used by the tenant student workspace. */
    @Transactional(readOnly = true)
    public Optional<StudentIdentityView> findStudentByTenant(UUID publicId, UUID tenantId) {
        return memberQuery.findStudentByTenant(publicId, tenantId);
    }

    public boolean existsById(UUID publicId) {
        return userRepository.findByPublicId(publicId).isPresent();
    }

    /** Whether the account holds an active membership in the organization (any role). */
    @Transactional(readOnly = true)
    public boolean isMemberOf(UUID userPublicId, UUID organizationId) {
        return memberQuery.isMemberOf(userPublicId, organizationId);
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
        return memberQuery.findByTenantIdAndRole(tenantId, role);
    }

    /** Returns only active, non-deleted recipients for tenant notifications. */
    @Transactional(readOnly = true)
    public List<User> findActiveByTenantIdAndRole(UUID tenantId, Role role) {
        return memberQuery.findActiveByTenantIdAndRole(tenantId, role);
    }

    /** Resolves an active, non-deleted student in exactly one tenant. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveStudentInTenant(UUID publicId, UUID tenantId) {
        return memberQuery.findActiveInTenant(publicId, tenantId, Role.STUDENT);
    }

    /** Resolves an active, non-deleted proctor in exactly one tenant. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveProctorInTenant(UUID publicId, UUID tenantId) {
        return memberQuery.findActiveInTenant(publicId, tenantId, Role.PROCTOR);
    }

    /** Resolves an active, non-deleted proctor or examiner in exactly one tenant. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveStaffInTenant(UUID publicId, UUID tenantId) {
        return memberQuery.findActiveStaffInTenant(publicId, tenantId);
    }

    /** Returns active students in one tenant in one repository call for batch commands. */
    @Transactional(readOnly = true)
    public Set<UUID> findActiveStudentPublicIds(UUID tenantId, Collection<UUID> publicIds) {
        return memberQuery.findActiveStudentPublicIds(tenantId, publicIds);
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
        return memberQuery.findActiveRoleMembers(role);
    }

    /** Returns active, nondeleted members of one role in one tenant. */
    @Transactional(readOnly = true)
    public List<IdentityRoleMember> findActiveRoleMembers(UUID tenantId, Role role) {
        return memberQuery.findActiveRoleMembers(tenantId, role);
    }

    /**
     * Holds the existing identity row serialization while a role-owned write commits. A null tenant is the exact
     * platform scope: the account's own roles; otherwise the membership in that tenant must carry the role.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveRoleMember(UUID publicId, UUID tenantId, Role role) {
        return memberLocks.lockActiveRoleMember(publicId, tenantId, role);
    }

    /**
     * Locks every requested active student identity in deterministic order for the duration of the caller
     * transaction. Enrollment uses this as the serialization boundary before reading slot conflicts, checking
     * capacity, and inserting rows.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActiveStudentsInTenant(Collection<UUID> publicIds, UUID tenantId) {
        memberLocks.lockActiveStudentsInTenant(publicIds, tenantId);
    }

    /** Returns only active EXAMINER identities in the supplied tenant; invalid IDs are omitted. */
    @Transactional(readOnly = true)
    public List<ExaminerIdentityView> findActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        return memberQuery.findActiveExaminers(tenantId, examinerPublicIds);
    }

    /** Display names of the tenant's users, keyed by public id; unknown or deleted ids are omitted. */
    @Transactional(readOnly = true)
    public Map<UUID, String> findFullNames(UUID tenantId, Collection<UUID> userPublicIds) {
        return memberQuery.findFullNames(tenantId, userPublicIds);
    }

    /**
     * Locks the selected tenant-owned memberships and returns only active Examiner identities. The caller must hold
     * one transaction through the protected write so suspension or role changes cannot race an assignment commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ExaminerIdentityView> lockActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        return memberLocks.lockActiveExaminers(tenantId, examinerPublicIds);
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
        return hostAdminProvisioner.createHostAdmin(tenantId, email);
    }

    /** Returns minimal display names and email addresses for enrolled students within the tenant. */
    @Transactional(readOnly = true)
    public Map<UUID, StudentDisplayNameView> getStudentDisplayNames(UUID tenantId,
            Collection<UUID> studentPublicIds) {
        return memberQuery.getStudentDisplayNames(tenantId, studentPublicIds);
    }

    /** Fresh active PROCTOR check for current tenant; ensures non-deleted and ACTIVE status. */
    @Transactional(readOnly = true)
    public boolean isActiveProctor(UUID userPublicId, UUID tenantId) {
        return findActiveProctorInTenant(userPublicId, tenantId).isPresent();
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
