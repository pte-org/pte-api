package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.QuotaTransaction;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.QuotaActionType;
import com.pte.tenancy.internal.exception.QuotaConflictException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.GrantQuotaRequest;
import com.pte.tenancy.internal.dto.response.QuotaTransactionResponse;
import com.pte.tenancy.internal.mapper.QuotaTransactionMapper;
import com.pte.tenancy.internal.repository.QuotaTransactionRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Package/quota ledger â€” a pure audit trail (business rule 5: traceable
 * allocation, no billing). This phase only ever writes {@code GRANTED}
 * rows; {@code DEDUCTED}/{@code REVOKED} are schema-ready for a future
 * deduct-on-exam-start flow, not implemented here.
 */
@Service
public class QuotaTransactionService {

    private final TenantRepository tenantRepository;
    private final QuotaTransactionRepository quotaTransactionRepository;

    public QuotaTransactionService(TenantRepository tenantRepository,
            QuotaTransactionRepository quotaTransactionRepository) {
        this.tenantRepository = tenantRepository;
        this.quotaTransactionRepository = quotaTransactionRepository;
    }

    @Transactional
    public QuotaTransactionResponse grant(UUID tenantPublicId, GrantQuotaRequest request, CurrentUser caller) {
        Tenant tenant = tenantRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);

        tenant.setPackageName(request.packageName());
        tenant.setStudentLimit(tenant.getStudentLimit() + request.amount());
        try {
            // Flush now (not just save) so a lost update â€” a concurrent grant
            // for the same tenant racing this one â€” throws HERE, inside this
            // try block, instead of surfacing later at commit time where it
            // could no longer be translated into a clean 409.
            tenantRepository.saveAndFlush(tenant);
        } catch (OptimisticLockingFailureException ex) {
            throw new QuotaConflictException();
        }

        QuotaTransaction transaction = new QuotaTransaction();
        transaction.setTenant(tenant);
        transaction.setPackageName(request.packageName());
        transaction.setAmount(request.amount());
        transaction.setActionType(QuotaActionType.GRANTED);
        transaction.setActorUserId(caller.userId());
        transaction.setNote(request.note());
        QuotaTransaction saved = quotaTransactionRepository.save(transaction);        return QuotaTransactionMapper.toResponse(saved, tenantPublicId);
    }

    /** Billing activation path for a capacity plan; records a system actor in the ledger. */
    @Transactional
    public QuotaTransactionResponse grantForSystem(UUID tenantPublicId, int amount, String note) {
        return grant(tenantPublicId,
                new GrantQuotaRequest(TenancyConstants.SYSTEM_QUOTA_PACKAGE, amount, note),
                new CurrentUser(TenancyConstants.SYSTEM_ACTOR_USER_ID, null, List.of("SYSTEM")));
    }

    @Transactional(readOnly = true)
    public List<QuotaTransactionResponse> history(UUID tenantPublicId, CurrentUser caller) {
        if (!tenantRepository.existsByPublicId(tenantPublicId)) {
            throw new TenantNotFoundException();
        }
        return quotaTransactionRepository.findByTenant_PublicIdOrderByCreatedAtDesc(tenantPublicId).stream()
                .map(transaction -> QuotaTransactionMapper.toResponse(transaction, tenantPublicId))
                .toList();
    }
}
