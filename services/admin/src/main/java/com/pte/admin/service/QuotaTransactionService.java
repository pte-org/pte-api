package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.QuotaTransaction;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.QuotaActionType;
import com.pte.admin.domain.event.QuotaGrantedEvent;
import com.pte.admin.domain.exception.QuotaConflictException;
import com.pte.admin.domain.exception.TenantNotFoundException;
import com.pte.admin.dto.request.GrantQuotaRequest;
import com.pte.admin.dto.response.QuotaTransactionResponse;
import com.pte.admin.mapper.QuotaTransactionMapper;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.QuotaTransactionRepository;
import com.pte.admin.repository.TenantRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Package/quota ledger — a pure audit trail (business rule 5: traceable
 * allocation, no billing). This phase only ever writes {@code GRANTED}
 * rows; {@code DEDUCTED}/{@code REVOKED} are schema-ready for a future
 * deduct-on-exam-start flow, not implemented here.
 */
@Service
public class QuotaTransactionService {

    private final TenantRepository tenantRepository;
    private final QuotaTransactionRepository quotaTransactionRepository;
    private final OutboxWriter outboxWriter;

    public QuotaTransactionService(TenantRepository tenantRepository,
            QuotaTransactionRepository quotaTransactionRepository, OutboxWriter outboxWriter) {
        this.tenantRepository = tenantRepository;
        this.quotaTransactionRepository = quotaTransactionRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public QuotaTransactionResponse grant(UUID tenantPublicId, GrantQuotaRequest request, CurrentUser caller) {
        Tenant tenant = tenantRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);

        tenant.setPackageName(request.packageName());
        tenant.setStudentLimit(tenant.getStudentLimit() + request.amount());
        try {
            // Flush now (not just save) so a lost update — a concurrent grant
            // for the same tenant racing this one — throws HERE, inside this
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
        QuotaTransaction saved = quotaTransactionRepository.save(transaction);

        outboxWriter.write(AdminConstants.AGGREGATE_QUOTA_TRANSACTION, saved.getPublicId().toString(),
                AdminConstants.EVENT_QUOTA_GRANTED,
                new QuotaGrantedEvent(saved.getPublicId(), tenantPublicId, saved.getPackageName(),
                        saved.getAmount(), saved.getActorUserId()),
                tenantPublicId);
        return QuotaTransactionMapper.toResponse(saved, tenantPublicId);
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
