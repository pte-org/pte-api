package com.pte.admin.mapper;

import com.pte.admin.domain.QuotaTransaction;
import com.pte.admin.dto.response.QuotaTransactionResponse;

import java.util.UUID;

/**
 * Takes {@code tenantPublicId} explicitly, same reasoning as
 * {@link OrganizationMapper}: callers already know it, so this never needs
 * to lazy-load {@code transaction.getTenant()} per row in a history list.
 */
public final class QuotaTransactionMapper {

    private QuotaTransactionMapper() {
    }

    public static QuotaTransactionResponse toResponse(QuotaTransaction transaction, UUID tenantPublicId) {
        return new QuotaTransactionResponse(
                transaction.getPublicId(),
                tenantPublicId,
                transaction.getPackageName(),
                transaction.getAmount(),
                transaction.getActionType().name(),
                transaction.getActorUserId(),
                transaction.getNote(),
                transaction.getCreatedAt());
    }
}
