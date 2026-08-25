package com.pte.admin.repository;

import com.pte.admin.domain.QuotaTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuotaTransactionRepository extends JpaRepository<QuotaTransaction, Long> {

    List<QuotaTransaction> findByTenant_PublicIdOrderByCreatedAtDesc(UUID tenantPublicId);
}
