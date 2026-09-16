package com.pte.tenancy;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.InvalidStudentCountException;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.tenancy.internal.service.TenantLifecycleService;
import com.pte.tenancy.internal.service.QuotaTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Public in-process API for tenant data needed by other modules. */
@Service
public class TenancyService {

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final TenantLifecycleService tenantLifecycleService;
    private final QuotaTransactionService quotaTransactionService;
    private final StudentCountProvider studentCountProvider;

    public TenancyService(OrganizationRepository organizationRepository, TenantRepository tenantRepository,
            TenantLifecycleService tenantLifecycleService, QuotaTransactionService quotaTransactionService,
            StudentCountProvider studentCountProvider) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
        this.tenantLifecycleService = tenantLifecycleService;
        this.quotaTransactionService = quotaTransactionService;
        this.studentCountProvider = studentCountProvider;
    }

    /** {@code billing.TenantApplicationService.submit()} — is this code still free to reserve? */
    public boolean existsByCode(String code) {
        return tenantRepository.existsByCode(code);
    }

    /** {@code billing.TenantApplicationService.approve()} — creates the tenant an approved application promised. */
    public Tenant createTenant(String name, String organizationType, String code, int studentLimit) {
        return tenantLifecycleService.createFromApplication(name, organizationType, code, studentLimit);
    }

    /** Billing activation path for STUDENT_CAPACITY plans. */
    public void grantQuota(UUID tenantPublicId, int amount, String note) {
        quotaTransactionService.grantForSystem(tenantPublicId, amount, note);
    }

    /** Returns the current student count and the tenant's effective limit. */
    @Transactional(readOnly = true)
    public StudentQuota getStudentQuota(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        Tenant tenant = tenantRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
        return new StudentQuota(studentCountProvider.countStudents(tenantPublicId), tenant.getStudentLimit());
    }

    /** Returns the effective student limit configured for a tenant. */
    @Transactional(readOnly = true)
    public int getStudentLimit(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        return tenantRepository.findByPublicId(tenantPublicId)
                .map(Tenant::getStudentLimit)
                .orElseThrow(TenantNotFoundException::new);
    }

    /** Returns the immutable tenant code used as the student username prefix. */
    @Transactional(readOnly = true)
    public String getTenantCode(UUID tenantPublicId) {
        requireTenantId(tenantPublicId);
        return tenantRepository.findByPublicId(tenantPublicId)
                .map(Tenant::getCode)
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
        Tenant tenant = tenantRepository.findWithLockByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
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
        return tenantRepository.findOrganizationTypeByPublicId(tenantId);
    }

    /** Resolves an organization only when it belongs to the supplied tenant. */
    public Organization findOrganizationOwned(UUID organizationPublicId, UUID tenantId) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (organization.getTenant() == null
                || !organization.getTenant().getPublicId().equals(tenantId)) {
            throw new OrganizationNotFoundException();
        }
        return organization;
    }
}
