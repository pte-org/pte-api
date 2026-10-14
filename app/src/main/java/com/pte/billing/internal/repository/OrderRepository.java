package com.pte.billing.internal.repository;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    Long nextOrderCode();

    boolean existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(UUID tenantId, UUID planId,
            OrderStatus status);

    Page<Order> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /** Overview: orders of the tenant in {@code status}; a count only, no payment data is read. */
    long countByTenantIdAndStatusAndDeletedFalse(UUID tenantId, OrderStatus status);

    /**
     * Overview (platform-wide): paid revenue and paid-order count per currency for orders paid in
     * {@code [from, to)}. Grouped by currency so amounts are never summed across currencies; reads
     * no customer or payment-provider field.
     */
    @Query("SELECT o.currency, SUM(o.amount), COUNT(o) FROM Order o WHERE o.deleted = false "
            + "AND o.status = :status AND o.paidAt >= :from AND o.paidAt < :to GROUP BY o.currency")
    List<Object[]> sumAmountAndCountByCurrencyPaidBetween(@Param("status") OrderStatus status,
            @Param("from") Instant from, @Param("to") Instant to);

    Page<Order> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<Order> findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(OrderStatus status,
            Instant createdAt);

    Optional<Order> findByPublicIdAndDeletedFalse(UUID publicId);

    Optional<Order> findByPublicIdAndTenantIdAndDeletedFalse(UUID publicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.publicId = :publicId and o.deleted = false")
    Optional<Order> findByPublicIdForUpdateAndDeletedFalse(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :orderCode and o.deleted = false")
    Optional<Order> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);
}
