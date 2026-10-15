package com.pte.billing;

import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only online revenue for the platform dashboard: paid orders only, always grouped per
 * currency (amounts of different currencies are never added together) and never mixed with
 * license-code activations, which carry no payment. Self-contained: it uses only this module's
 * order repository, so it adds no dependency to {@code billing}. No customer, tenant or
 * payment-provider data leaves billing through this facade, only amounts and counts.
 */
@Service
public class BillingRevenueOverviewService {

    private final OrderRepository orderRepository;

    public BillingRevenueOverviewService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Exact paid amount (as stored) and paid-order count of one currency over a range. */
    public record CurrencyPaidTotal(String currency, BigDecimal amount, long paidOrders) {
    }

    /**
     * Paid orders whose {@code paidAt} falls in {@code [from, to)}, one entry per currency that has
     * any, ordered by currency code.
     */
    @Transactional(readOnly = true)
    public List<CurrencyPaidTotal> paidRevenueBetween(Instant from, Instant to) {
        return queryPaid(from, to);
    }

    /**
     * Paid amount per consecutive range: {@code boundaries} holds n+1 ascending instants and every
     * currency that has revenue in any range maps to n amounts (zero where it has none), one per
     * {@code [boundaries[i], boundaries[i+1])}. Currencies are ordered by code.
     */
    @Transactional(readOnly = true)
    public Map<String, List<BigDecimal>> paidAmountsPerRange(List<Instant> boundaries) {
        if (boundaries == null || boundaries.size() < 2) {
            return Map.of();
        }
        int ranges = boundaries.size() - 1;
        Map<String, List<BigDecimal>> perCurrency = new TreeMap<>();
        for (int i = 0; i < ranges; i++) {
            for (CurrencyPaidTotal revenue : queryPaid(boundaries.get(i), boundaries.get(i + 1))) {
                perCurrency.computeIfAbsent(revenue.currency(),
                        currency -> new ArrayList<>(Collections.nCopies(ranges, BigDecimal.ZERO)))
                        .set(i, revenue.amount());
            }
        }
        perCurrency.replaceAll((currency, amounts) -> List.copyOf(amounts));
        return Collections.unmodifiableMap(perCurrency);
    }

    private List<CurrencyPaidTotal> queryPaid(Instant from, Instant to) {
        return orderRepository.sumAmountAndCountByCurrencyPaidBetween(OrderStatus.PAID, from, to).stream()
                .map(row -> new CurrencyPaidTotal((String) row[0], toAmount(row[1]), ((Number) row[2]).longValue()))
                .sorted(Comparator.comparing(CurrencyPaidTotal::currency))
                .toList();
    }

    private static BigDecimal toAmount(Object sum) {
        return sum instanceof BigDecimal amount ? amount : new BigDecimal(String.valueOf(sum));
    }
}
