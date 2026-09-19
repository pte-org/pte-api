package com.pte.billing.internal.repository;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    Long nextOrderCode();

    boolean existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(UUID tenantId, UUID planId,
            OrderStatus status);

    Page<Order> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<Order> findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(OrderStatus status,
            Instant createdAt);

    Optional<Order> findByPublicIdAndDeletedFalse(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :orderCode and o.deleted = false")
    Optional<Order> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);
}
