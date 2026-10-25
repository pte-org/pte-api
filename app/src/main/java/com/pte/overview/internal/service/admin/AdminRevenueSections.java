package com.pte.overview.internal.service.admin;

import com.pte.overview.internal.service.DashboardWindow;
import com.pte.overview.internal.service.MonthBuckets;
import com.pte.overview.internal.service.TrendCalculator;
import com.pte.billing.BillingRevenueOverviewService;
import com.pte.billing.BillingRevenueOverviewService.CurrencyPaidTotal;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminSectionData.CurrencyRevenue;
import com.pte.overview.internal.dto.response.AdminSectionData.Revenue;
import com.pte.overview.internal.dto.response.TrendValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payload builder for the revenue admin section: online revenue from paid orders, one entry per
 * currency (never summed across currencies, never mixed with license-code activations). The
 * permission check (commercial read) happens in the caller, before this runs.
 */
@Component
public class AdminRevenueSections {

    private final BillingRevenueOverviewService billing;

    public AdminRevenueSections(BillingRevenueOverviewService billing) {
        this.billing = billing;
    }

    public Revenue revenue(DashboardWindow w) {
        List<Instant> boundaries = MonthBuckets.boundaries(w.end(), OverviewConstants.BUCKET_ZONE_ID,
                OverviewConstants.SERIES_MONTHS);
        Map<String, List<BigDecimal>> series = billing.paidAmountsPerRange(boundaries);
        Map<String, CurrencyPaidTotal> current = byCurrency(billing.paidRevenueBetween(w.start(), w.end()));
        Map<String, CurrencyPaidTotal> previous = byCurrency(
                billing.paidRevenueBetween(w.previousStart(), w.start()));

        Set<String> currencies = new TreeSet<>(series.keySet());
        currencies.addAll(current.keySet());
        currencies.addAll(previous.keySet());
        List<BigDecimal> emptySeries = Collections.nCopies(OverviewConstants.SERIES_MONTHS, BigDecimal.ZERO);
        List<CurrencyRevenue> lines = currencies.stream().map(currency -> {
            CurrencyPaidTotal now = current.get(currency);
            CurrencyPaidTotal before = previous.get(currency);
            BigDecimal currentAmount = amountOf(now);
            BigDecimal previousAmount = amountOf(before);
            return new CurrencyRevenue(currency, currentAmount, previousAmount,
                    TrendCalculator.percentChange(currentAmount, previousAmount),
                    TrendCalculator.of(ordersOf(now), ordersOf(before)),
                    MonthBuckets.toAmountPoints(w.end(), OverviewConstants.BUCKET_ZONE_ID,
                            series.getOrDefault(currency, emptySeries)));
        }).toList();

        TrendValue paidOrders = TrendCalculator.of(totalOrders(current), totalOrders(previous));
        return new Revenue(OverviewConstants.REVENUE_SOURCE_ONLINE_ORDERS, paidOrders, lines);
    }

    private static Map<String, CurrencyPaidTotal> byCurrency(List<CurrencyPaidTotal> totals) {
        return totals.stream().collect(Collectors.toMap(CurrencyPaidTotal::currency, Function.identity()));
    }

    private static BigDecimal amountOf(CurrencyPaidTotal total) {
        return total == null ? BigDecimal.ZERO : total.amount();
    }

    private static long ordersOf(CurrencyPaidTotal total) {
        return total == null ? 0L : total.paidOrders();
    }

    private static long totalOrders(Map<String, CurrencyPaidTotal> totals) {
        return totals.values().stream().mapToLong(CurrencyPaidTotal::paidOrders).sum();
    }
}
