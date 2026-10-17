package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.OrderResponse;
import com.pte.billing.internal.exception.OrderException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.constant.SharedConstants;
import com.pte.tenancy.TenancyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;

/** Creates PayOS orders and exposes tenant or platform-scoped order projections. */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PlanRepository planRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final PayOsClient payOsClient;
    private final AuditLogService auditLogService;
    private final TenancyService tenancyService;

    @Autowired
    public OrderService(OrderRepository orderRepository, PlanRepository planRepository,
            OrderPersistenceService orderPersistenceService, PayOsClient payOsClient,
            AuditLogService auditLogService) {
        this(orderRepository, planRepository, orderPersistenceService, payOsClient, auditLogService, null);
    }

    @Autowired
    public OrderService(OrderRepository orderRepository, PlanRepository planRepository,
            OrderPersistenceService orderPersistenceService, PayOsClient payOsClient,
            AuditLogService auditLogService, TenancyService tenancyService) {
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.orderPersistenceService = orderPersistenceService;
        this.payOsClient = payOsClient;
        this.auditLogService = auditLogService;
        this.tenancyService = tenancyService;
    }

    /** Compatibility constructor for focused billing tests. */
    public OrderService(OrderRepository orderRepository, PlanRepository planRepository,
            OrderPersistenceService orderPersistenceService, PayOsClient payOsClient) {
        this(orderRepository, planRepository, orderPersistenceService, payOsClient, null);
    }

    public OrderResponse createOrder(UUID tenantId, UUID planPublicId) {
        if (tenantId == null) {
            throw invalid(BillingConstants.ORDER_TENANT_REQUIRED);
        }
        if (planPublicId == null) {
            throw invalid(BillingConstants.ORDER_PLAN_REQUIRED);
        }
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }

        Plan plan = planRepository.findByPublicId(planPublicId)
                .orElseThrow(PlanNotFoundException::new);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PLAN_NOT_ACTIVE);
        }
        validateOrderAmount(plan.getPrice(), plan.getCurrency());
        if (orderRepository.existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(
                tenantId, planPublicId, OrderStatus.PENDING)) {
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PENDING_EXISTS);
        }

        Order order = Order.pending(tenantId, planPublicId, orderRepository.nextOrderCode(),
                plan.getPrice(), plan.getCurrency().toUpperCase(Locale.ROOT));
        try {
            order = orderPersistenceService.reserve(order);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index is the final race-safe guard when two
            // browser requests pass the read-side pending check together.
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PENDING_EXISTS);
        }

        if (plan.getPrice().signum() == 0) {
            return OrderResponse.from(orderPersistenceService.completeFreeOrder(order.getPublicId()));
        }

        String paymentLinkUrl = payOsClient.createPaymentLink(order);
        return OrderResponse.from(orderPersistenceService.attachPaymentLink(
                order.getPublicId(), paymentLinkUrl));
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> listOrders(UUID tenantId, int requestedPage, int requestedSize) {
        return listOrders(tenantId, null, null, null, null, requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> listOrders(UUID tenantId, String search, int requestedPage,
            int requestedSize) {
        return listOrders(tenantId, search, null, null, null, requestedPage, requestedSize);
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> listOrders(UUID tenantId, String search, OrderStatus status,
            LocalDate createdFrom, LocalDate createdTo, int requestedPage, int requestedSize) {
        if (tenantId == null) {
            throw invalid(BillingConstants.ORDER_TENANT_REQUIRED);
        }
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, 100);
        boolean hasFilters = (search != null && !search.trim().isBlank()) || status != null
                || createdFrom != null || createdTo != null;
        Page<Order> orders = !hasFilters
                ? orderRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId,
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"))))
                : orderRepository.findAll(orderFilter(tenantId, search, status, createdFrom, createdTo),
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"))));
        return new PagedResult<>(orders.map(OrderResponse::from).getContent(),
                new PageMeta(orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages(),
                        orders.isFirst(), orders.isLast(), orders.hasNext(), orders.hasPrevious()));
    }

    private Specification<Order> orderFilter(UUID tenantId, String search, OrderStatus status,
            LocalDate createdFrom, LocalDate createdTo) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.equal(root.get("tenantId"), tenantId));
            filters.add(cb.isFalse(root.get("deleted")));
            if (status != null) {
                filters.add(cb.equal(root.get("status"), status));
            }
            if (createdFrom != null) {
                filters.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"),
                        createdFrom.atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (createdTo != null) {
                filters.add(cb.lessThan(root.<Instant>get("createdAt"),
                        createdTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
                return cb.disjunction();
            }
            if (normalized.isBlank()) {
                return cb.and(filters.toArray(Predicate[]::new));
            }
            var plan = query.from(Plan.class);
            filters.add(cb.equal(plan.get("publicId"), root.get("planId")));
            filters.add(cb.isFalse(plan.get("deleted")));
            String pattern = "%" + normalized + "%";
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(cb.lower(cb.toString(root.get("orderCode"))), pattern));
            matches.add(cb.like(cb.lower(root.get("status").as(String.class)), pattern));
            matches.add(cb.like(cb.lower(plan.get("name")), pattern));
            filters.add(cb.or(matches.toArray(Predicate[]::new)));
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> listPlatformOrders(int requestedPage, int requestedSize, CurrentUser caller) {
        return listPlatformOrders(requestedPage, requestedSize, null, caller);
    }

    @Transactional(readOnly = true)
    public PagedResult<OrderResponse> listPlatformOrders(int requestedPage, int requestedSize, UUID tenantId,
            CurrentUser caller) {
        requirePlatformRead(caller);
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, 100);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = tenantId == null
                ? orderRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                : orderRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, pageable);
        return new PagedResult<>(orders.map(OrderResponse::from).getContent(),
                new PageMeta(orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages(),
                        orders.isFirst(), orders.isLast(), orders.hasNext(), orders.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            throw new OrderException(HttpStatus.NOT_FOUND, BillingConstants.ORDER_NOT_FOUND);
        }
        return orderRepository.findByPublicIdAndTenantIdAndDeletedFalse(publicId, tenantId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, BillingConstants.ORDER_NOT_FOUND));
    }

    private void validateOrderAmount(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw invalid(BillingConstants.ORDER_AMOUNT_INVALID);
        }
        try {
            amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw invalid(BillingConstants.ORDER_AMOUNT_INVALID);
        }
        if (currency == null || !BillingConstants.PAYOS_CURRENCY.equalsIgnoreCase(currency)) {
            throw invalid(BillingConstants.ORDER_CURRENCY_UNSUPPORTED);
        }
    }

    private OrderException invalid(String code) {
        return new OrderException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }

    private void requirePlatformRead(CurrentUser caller) {
        if (PlatformOperationPolicy.can(caller, PlatformOperation.COMMERCIAL_READ)) {
            return;
        }
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, BillingConstants.ORDER_AGGREGATE, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, PlatformOperation.COMMERCIAL_READ.name());
        }
        throw new org.springframework.security.access.AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }
}
