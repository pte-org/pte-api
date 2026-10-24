package com.pte.tenancy;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.InvalidStudentCountException;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.exception.TenantSuspendedException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.service.OrganizationLifecycleService;
import com.pte.tenancy.internal.service.QuotaGrantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Public in-process API for tenant data needed by other modules. */
@Service
public class TenancyService {

    private final OrganizationRepository organizationRepository;
        private final OrganizationLifecycleService organizationLifecycleService;
    private final QuotaGrantService quotaGrantService;
    private final StudentCountProvider studentCountProvider;

    public TenancyService(OrganizationRepository organizationRepository,
            OrganizationLifecycleService organizationLifecycleService, QuotaGrantService quotaGrantService,
            StudentCountProvider studentCountProvider) {
        this.organizationRepository = organizationRepository;
        this.organizationLifecycleService = organizationLifecycleService;
        this.quotaGrantService = quotaGrantService;
        this.studentCountProvider = studentCountProvider;
    }

    /** {@code billing.TenantApplicationService.submit()} — is this code still free to reserve? */
    public boolean existsByCode(String code) {
        return organizationRepository.existsByCodeAndStatusNot(code, OrganizationStatus.REJECTED);
    }

    /** {@code billing.TenantApplicationService.submit()} — is this name already reserved? */
    public boolean existsByName(String name) {
        return organizationRepository.existsByNameAndStatusNot(name, OrganizationStatus.REJECTED);
    }

    /** Reuses the tenant lock; acquire before identity locks when guarding tenant-owned delivery. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveTenant(UUID tenantPublicId) {
        if (tenantPublicId == null) { return false; }
        return organizationRepository.findWithLockByPublicId(tenantPublicId)
                .filter(tenant -> !tenant.isDeleted() && tenant.getStatus() == OrganizationStatus.ACTIVE)
                .isPresent();
    }

    /** Resolves the active/nondeleted tenant subset without exposing the repository. */
    @Transactional(readOnly = true)
    public Set<UUID> findActiveTenantIds(Collection<UUID> tenantPublicIds) {
        if (tenantPublicIds == null || tenantPublicIds.isEmpty()) {
            return Set.of();
        }
        return organizationRepository.findByPublicIdInAndStatusAndDeletedFalse(tenantPublicIds, OrganizationStatus.ACTIVE)
                .stream().map(Organization::getPublicId).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns safe tenant identity projections in one repository query. The
     * deleted bit lets callers render legacy references without treating a
     * missing/deleted tenant as an empty result.
     */
    @Transactional(readOnly = true)
    public Map<UUID, TenantSummary> findTenantSummaries(Collection<UUID> tenantPublicIds) {
        if (tenantPublicIds == null || tenantPublicIds.isEmpty()) {
            return Map.of();
        }
        return organizationRepository.findByPublicIdIn(tenantPublicIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        Organization::getPublicId,
                        tenant -> new TenantSummary(tenant.getPublicId(), tenant.getName(), tenant.isDeleted())));
    }

    /** Returns safe tenant identity and organization-type projections in one query. */
    @Transactional(readOnly = true)
    public Map<UUID, TenantOrganizationSummary> findTenantOrganizationSummaries(
            Collection<UUID> tenantPublicIds) {
        if (tenantPublicIds == null || tenantPublicIds.isEmpty()) {
            return Map.of();
        }
        return organizationRepository.findByPublicIdIn(tenantPublicIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        Organization::getPublicId,
                        tenant -> new TenantOrganizationSummary(
                                tenant.getPublicId(), tenant.getName(), tenant.getOrganizationType(),
                                tenant.isDeleted())));
    }

    /** Billing: records a public onboarding application as a PENDING organization (joins the caller's transaction). */
    public Organization submitApplication(String name, String organizationType, String code, String taxCode,
            String contactEmail, String contactPhone) {
        return organizationLifecycleService.submitApplication(name, organizationType, code, taxCode, contactEmail,
                contactPhone);
    }

    /** Billing: approves a locked pending application and grants its free student limit. */
    public Organization approveApplication(Organization application, java.util.UUID reviewerPublicId,
            int studentLimit) {
        return organizationLifecycleService.approveApplication(application, reviewerPublicId, studentLimit);
    }

    /** Billing: rejects a locked pending application; code and name become free again. */
    public Organization rejectApplication(Organization application, java.util.UUID reviewerPublicId, String reason) {
        return organizationLifecycleService.rejectApplication(application, reviewerPublicId, reason);
    }

    @Transactional(readOnly = true)
    public List<Organization> listApplications() {
        return organizationRepository.findAllApplications();
    }

    @Transactional(readOnly = true)
    public Optional<Organization> findApplication(UUID applicationPublicId) {
        return organizationRepository.findApplication(applicationPublicId);
    }

    /** Pessimistic lock so two reviewers cannot decide the same application. */
    public Optional<Organization> findApplicationForUpdate(UUID applicationPublicId) {
        return organizationRepository.findApplicationForUpdate(applicationPublicId);
    }

    @Transactional(readOnly = true)
    public long countPendingApplications() {
        return organizationRepository.countApplicationsByStatus(OrganizationStatus.PENDING);
    }

    public long countApplicationsByStatus(OrganizationStatus status) {
        return organizationRepository.countApplicationsByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countApplicationsReceivedBetween(java.time.Instant from, java.time.Instant to) {
        return organizationRepository.countApplicationsReceivedBetween(from, to);
    }

    @Transactional(readOnly = true)
    public List<Organization> findOldestPendingApplications(int limit) {
        return organizationRepository.findOldestApplicationsByStatus(OrganizationStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /** Billing activation path for STUDENT_CAPACITY plans. */
    public UUID grantQuota(UUID tenantPublicId, int amount, String note) {
        return quotaGrantService.grantForSystem(tenantPublicId, amount, note).publicId();
    }

    /** Returns whether a tenant is active and not soft-deleted for fan-out decisions. */
    @Transactional(readOnly = true)
    public boolean isActiveTenant(UUID tenantPublicId) {
        return tenantPublicId != null
                && organizationRepository.findByPublicId(tenantPublicId)
                .filter(tenant -> !tenant.isDeleted() && tenant.getStatus() == OrganizationStatus.ACTIVE)
                .isPresent();
    }

    /** Guards the start of tenant-owned writes; running attempts use no such guard. */
    @Transactional(readOnly = true)
    public void assertTenantActive(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        Organization tenant = organizationRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.isDeleted()) {
            throw new TenantNotFoundException();
        }
        if (tenant.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new TenantSuspendedException();
        }
    }

    /** Returns the current student count and the tenant's effective limit. */
    @Transactional(readOnly = true)
    public StudentQuota getStudentQuota(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        Organization tenant = organizationRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
        return new StudentQuota(studentCountProvider.countStudents(tenantPublicId), tenant.getStudentLimit());
    }

    /** Dashboard read: number of non-deleted organizations (branches) a tenant owns. */
    @Transactional(readOnly = true)
    public long countOrganizations(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        return organizationRepository.existsByPublicId(tenantPublicId) ? 1L : 0L;
    }

    /** Returns the effective student limit configured for a tenant. */
    @Transactional(readOnly = true)
    public int getStudentLimit(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        return organizationRepository.findByPublicId(tenantPublicId)
                .map(Organization::getStudentLimit)
                .orElseThrow(TenantNotFoundException::new);
    }

    /** Returns the immutable tenant code used as the student username prefix. */
    @Transactional(readOnly = true)
    public String getTenantCode(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        return organizationRepository.findByPublicId(tenantPublicId)
                .map(Organization::getCode)
                .orElseThrow(TenantNotFoundException::new);
    }

    /**
     * Locks the tenant row for the caller's transaction, then checks the live
     * student count. The lock must remain held until the caller's student
     * writes commit; UserService provides that transaction around both single
     * and bulk provisioning.
     */
    @Transactional
    public void assertCanAddStudents(UUID tenantPublicId, long adding) {
        if (adding <= 0L) {
            throw new InvalidStudentCountException();
        }
        requireTenantId(tenantPublicId);
        Organization tenant = organizationRepository.findWithLockByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.isDeleted()) {
            throw new TenantNotFoundException();
        }
        if (tenant.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new TenantSuspendedException();
        }
        long current = studentCountProvider.countStudents(tenantPublicId);
        StudentQuota quota = new StudentQuota(current, tenant.getStudentLimit());
        if (!quota.canAdd(adding)) {
            throw new StudentLimitExceededException(current, quota.limit(), adding);
        }
    }

    private void requireTenantId(UUID tenantPublicId) {
        if (tenantPublicId == null) {
            throw new TenantNotFoundException();
        }
    }

    /** Returns the tenant's display type without exposing tenancy repositories. */
    public Optional<String> findOrganizationType(UUID tenantId) {
        return organizationRepository.findOrganizationTypeByPublicId(tenantId);
    }

    /**
     * Returns safe tenant metadata for credential-matched login disambiguation.
     * Suspended tenants remain selectable because suspension blocks new tenant
     * activity, not authentication or access to in-flight/report data.
     */
    @Transactional(readOnly = true)
    public Optional<LoginOrganizationOption> findLoginOrganization(UUID tenantId) {
        return organizationRepository.findByPublicId(tenantId)
                .filter(tenant -> !tenant.isDeleted())
                .map(tenant -> new LoginOrganizationOption(
                        tenant.getPublicId(), tenant.getCode(), tenant.getName(), tenant.getOrganizationType()));
    }

    /**
     * Resolves the caller's own organization. Since an organization is the tenant, the id must equal the tenant id;
     * anything else is reported as not found so ids from another tenant never leak.
     */
    @Transactional(readOnly = true)
    public Organization findOrganizationOwned(UUID organizationPublicId, UUID tenantId) {
        if (tenantId == null || !tenantId.equals(organizationPublicId)) {
            throw new OrganizationNotFoundException();
        }
        return organizationRepository.findByPublicId(organizationPublicId)
                .filter(organization -> !organization.isDeleted())
                .orElseThrow(OrganizationNotFoundException::new);
    }
}
