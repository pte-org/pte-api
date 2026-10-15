package com.pte.overview.internal.service;

import com.pte.billing.BillingRevenueOverviewService.CurrencyPaidTotal;
import com.pte.overview.internal.AdminDashboardFixtures;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminSectionData.CurrencyRevenue;
import com.pte.overview.internal.dto.response.AdminSectionData.Revenue;
import com.pte.overview.internal.dto.response.MonthlyAmount;
import com.pte.overview.internal.dto.response.TrendValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pte.overview.internal.AdminDashboardFixtures.NOW;
import static com.pte.overview.internal.AdminDashboardFixtures.PREVIOUS_START;
import static com.pte.overview.internal.AdminDashboardFixtures.WINDOW_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Payload composition of the revenue admin section over the mocked billing revenue facade: per-currency mapping,
 * trend maths on exact amounts, the 12-month series and currency selection. The facade queries themselves are
 * covered by BillingRevenueOverviewQueriesTest in the billing module.
 */
class AdminRevenueSectionsTest {

    private static final List<BigDecimal> ZEROS = Collections.nCopies(12, BigDecimal.ZERO);

    private AdminDashboardFixtures fx;
    private AdminRevenueSections sections;
    private DashboardWindow window;

    @BeforeEach
    void setUp() {
        fx = new AdminDashboardFixtures().stubHappyPath();
        sections = fx.revenueSections();
        window = DashboardWindow.of(30, NOW);
    }

    private static CurrencyRevenue line(Revenue revenue, String currency) {
        return revenue.currencies().stream().filter(c -> c.currency().equals(currency)).findFirst().orElseThrow();
    }

    private void stubNothing() {
        reset(fx.revenueFacade);
        when(fx.revenueFacade.paidRevenueBetween(WINDOW_START, NOW)).thenReturn(List.of());
        when(fx.revenueFacade.paidRevenueBetween(PREVIOUS_START, WINDOW_START)).thenReturn(List.of());
        when(fx.revenueFacade.paidAmountsPerRange(anyList())).thenReturn(Map.of());
    }

    // ---- mapping ----

    @Test
    void theSectionIsLabelledOnlineOrdersAndListsEveryCurrencySeenInEitherWindowOrderedByCode() {
        Revenue revenue = sections.revenue(window);

        assertThat(revenue.source()).isEqualTo("ONLINE_ORDERS").isEqualTo(OverviewConstants.REVENUE_SOURCE_ONLINE_ORDERS);
        // EUR only in the previous window, USD only in the current one, VND in both
        assertThat(revenue.currencies()).extracting(CurrencyRevenue::currency).containsExactly("EUR", "USD", "VND");
    }

    @Test
    void eachCurrencyCarriesItsOwnCurrentPreviousChangeAndPaidOrderTrend() {
        Revenue revenue = sections.revenue(window);

        CurrencyRevenue vnd = line(revenue, "VND");
        assertThat(vnd.current()).isEqualTo(new BigDecimal("1500000.50"));
        assertThat(vnd.previous()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(vnd.changePct()).isEqualTo(50.0);
        assertThat(vnd.paidOrders()).isEqualTo(new TrendValue(3, 2, 50.0));

        CurrencyRevenue usd = line(revenue, "USD");
        assertThat(usd.current()).isEqualTo(new BigDecimal("120.00"));
        assertThat(usd.previous()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(usd.changePct()).as("previous is zero: no invented percentage").isNull();
        assertThat(usd.paidOrders()).isEqualTo(new TrendValue(2, 0, null));

        CurrencyRevenue eur = line(revenue, "EUR");
        assertThat(eur.current()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(eur.previous()).isEqualTo(new BigDecimal("50.00"));
        assertThat(eur.changePct()).isEqualTo(-100.0);
        assertThat(eur.paidOrders()).isEqualTo(new TrendValue(0, 1, -100.0));
    }

    @Test
    void theOverallPaidOrderTrendCountsOrdersOfAllCurrenciesButNeverAddsAmounts() {
        Revenue revenue = sections.revenue(window);

        // current 2 (USD) + 3 (VND) = 5, previous 1 (EUR) + 2 (VND) = 3
        assertThat(revenue.paidOrders()).isEqualTo(new TrendValue(5, 3, 66.7));
        // amounts of different currencies are never combined: no line holds a sum of two currencies
        assertThat(revenue.currencies()).extracting(CurrencyRevenue::current)
                .containsExactlyInAnyOrder(BigDecimal.ZERO, new BigDecimal("120.00"), new BigDecimal("1500000.50"));
    }

    @Test
    void currenciesComeOutOrderedByCodeWhateverOrderTheFacadeReturnsThem() {
        stubNothing();
        when(fx.revenueFacade.paidRevenueBetween(WINDOW_START, NOW)).thenReturn(List.of(
                new CurrencyPaidTotal("VND", new BigDecimal("10.00"), 1), new CurrencyPaidTotal("AUD", new BigDecimal("1.00"), 1),
                new CurrencyPaidTotal("USD", new BigDecimal("5.00"), 1)));

        assertThat(sections.revenue(window).currencies()).extracting(CurrencyRevenue::currency)
                .containsExactly("AUD", "USD", "VND");
    }

    // ---- 12-month series ----

    @Test
    void theMonthlySeriesHasTwelveMonthsOldestFirstWithOnlyTheLastMonthPartial() {
        Revenue revenue = sections.revenue(window);

        List<MonthlyAmount> vnd = line(revenue, "VND").monthly();
        assertThat(vnd).hasSize(12);
        assertThat(vnd).extracting(MonthlyAmount::month).containsExactly("2025-11", "2025-12", "2026-01", "2026-02",
                "2026-03", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(vnd).extracting(MonthlyAmount::partial)
                .containsExactly(false, false, false, false, false, false, false, false, false, false, false, true);
        assertThat(vnd.get(10).amount()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(vnd.get(11).amount()).isEqualTo(new BigDecimal("1500000.50"));
        assertThat(vnd.subList(0, 10)).extracting(MonthlyAmount::amount).allMatch(a -> a.signum() == 0);
    }

    @Test
    void aCurrencyWithoutSeriesRowsGetsTwelveZeroMonthsSoTheChartAlwaysHasTwelvePoints() {
        // EUR is only present in the previous window (not in the stubbed series)
        List<MonthlyAmount> eur = line(sections.revenue(window), "EUR").monthly();

        assertThat(eur).hasSize(12);
        assertThat(eur).extracting(MonthlyAmount::amount).allMatch(a -> a.signum() == 0);
        assertThat(eur.get(11).partial()).isTrue();
        assertThat(eur.get(0).month()).isEqualTo("2025-11");
    }

    @Test
    void aCurrencyThatAppearsOnlyInOlderMonthsIsStillListedWithZeroWindowsAndANullChange() {
        stubNothing();
        List<BigDecimal> old = new java.util.ArrayList<>(ZEROS);
        old.set(2, new BigDecimal("77.70"));
        when(fx.revenueFacade.paidAmountsPerRange(anyList())).thenReturn(Map.of("GBP", List.copyOf(old)));

        Revenue revenue = sections.revenue(window);

        assertThat(revenue.currencies()).hasSize(1);
        CurrencyRevenue gbp = revenue.currencies().get(0);
        assertThat(gbp.currency()).isEqualTo("GBP");
        assertThat(gbp.current()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(gbp.previous()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(gbp.changePct()).isNull();
        assertThat(gbp.paidOrders()).isEqualTo(new TrendValue(0, 0, null));
        assertThat(gbp.monthly().get(2).amount()).isEqualTo(new BigDecimal("77.70"));
        assertThat(gbp.monthly().get(2).month()).isEqualTo("2026-01");
        assertThat(revenue.paidOrders()).isEqualTo(new TrendValue(0, 0, null));
    }

    @Test
    void theBoundariesPassedToTheFacadeAreTheVietnamMonthsAroundTheInstantNotUtc() {
        // 2026-10-31T17:30Z is already 1 November in Asia/Ho_Chi_Minh
        Instant boundaryNow = Instant.parse("2026-10-31T17:30:00Z");
        DashboardWindow boundaryWindow = DashboardWindow.of(30, boundaryNow);
        stubNothing();

        Revenue revenue = sections.revenue(boundaryWindow);

        verify(fx.revenueFacade).paidAmountsPerRange(MonthBuckets.boundaries(boundaryNow, OverviewConstants.BUCKET_ZONE_ID, 12));
        verify(fx.revenueFacade).paidAmountsPerRange(org.mockito.ArgumentMatchers.argThat(list ->
                list.size() == 13 && list.get(11).equals(Instant.parse("2026-10-31T17:00:00Z"))
                        && list.get(12).equals(Instant.parse("2026-11-30T17:00:00Z"))));
        assertThat(revenue.currencies()).isEmpty();
    }

    @Test
    void theLastMonthLabelFollowsTheVietnamCalendarAtTheMonthBoundary() {
        Instant boundaryNow = Instant.parse("2026-10-31T17:30:00Z");
        stubNothing();
        when(fx.revenueFacade.paidAmountsPerRange(anyList())).thenReturn(Map.of("VND", ZEROS));

        List<MonthlyAmount> months = sections.revenue(DashboardWindow.of(30, boundaryNow)).currencies().get(0).monthly();

        assertThat(months.get(11).month()).isEqualTo("2026-11");
        assertThat(months.get(11).partial()).isTrue();
        assertThat(months.get(0).month()).isEqualTo("2025-12");
    }

    // ---- empty / exactness ----

    @Test
    void withNoPaidOrdersAtAllTheSectionIsEmptyButStillOk() {
        stubNothing();

        Revenue revenue = sections.revenue(window);

        assertThat(revenue.source()).isEqualTo("ONLINE_ORDERS");
        assertThat(revenue.currencies()).isEmpty();
        assertThat(revenue.paidOrders()).isEqualTo(new TrendValue(0, 0, null));
    }

    @Test
    void amountsStayExactBigDecimalsWithTheirScaleNeverPassedThroughDouble() {
        stubNothing();
        when(fx.revenueFacade.paidRevenueBetween(WINDOW_START, NOW)).thenReturn(List.of(
                new CurrencyPaidTotal("VND", new BigDecimal("12345678901234567.89"), 4)));
        when(fx.revenueFacade.paidRevenueBetween(PREVIOUS_START, WINDOW_START)).thenReturn(List.of(
                new CurrencyPaidTotal("VND", new BigDecimal("12345678901234567.88"), 4)));

        CurrencyRevenue vnd = sections.revenue(window).currencies().get(0);

        assertThat(vnd.current()).isEqualTo(new BigDecimal("12345678901234567.89"));
        assertThat(vnd.current().scale()).isEqualTo(2);
        assertThat(vnd.previous()).isEqualTo(new BigDecimal("12345678901234567.88"));
        // 0.01 / 12345678901234567.88 is far below 0.05 percent: exactly zero change after rounding
        assertThat(vnd.changePct()).isEqualTo(0.0);
        assertThat(vnd.paidOrders()).isEqualTo(new TrendValue(4, 4, 0.0));
    }

    @Test
    void theChangeIsComputedPerCurrencyOnExactAmountsHalfUpToOneDecimal() {
        stubNothing();
        when(fx.revenueFacade.paidRevenueBetween(WINDOW_START, NOW)).thenReturn(List.of(
                new CurrencyPaidTotal("USD", new BigDecimal("2001.00"), 1),
                new CurrencyPaidTotal("VND", new BigDecimal("1999.00"), 1)));
        when(fx.revenueFacade.paidRevenueBetween(PREVIOUS_START, WINDOW_START)).thenReturn(List.of(
                new CurrencyPaidTotal("USD", new BigDecimal("2000.00"), 1),
                new CurrencyPaidTotal("VND", new BigDecimal("2000.00"), 1)));

        Revenue revenue = sections.revenue(window);

        assertThat(line(revenue, "USD").changePct()).isEqualTo(0.1);
        assertThat(line(revenue, "VND").changePct()).isEqualTo(-0.1);
    }

    // ---- collaboration and data hygiene ----

    @Test
    void theSectionReadsOnlyTheRevenueFacadeWithTwoWindowsAndOneSeriesCall() {
        sections.revenue(window);

        verify(fx.revenueFacade).paidRevenueBetween(WINDOW_START, NOW);
        verify(fx.revenueFacade).paidRevenueBetween(PREVIOUS_START, WINDOW_START);
        verify(fx.revenueFacade).paidAmountsPerRange(MonthBuckets.boundaries(NOW, OverviewConstants.BUCKET_ZONE_ID, 12));
        verifyNoMoreInteractions(fx.revenueFacade);
        // license-code activations (billing) and every other source are never read: online orders only
        verifyNoInteractions(fx.billing, fx.tenancy, fx.tenancyOverview, fx.identity, fx.attempts, fx.support, fx.itembank);
    }

    @Test
    void thePayloadRecordsHaveNoCustomerTenantPaymentOrLicenseField() {
        List<String> names = new java.util.ArrayList<>();
        for (Class<?> type : List.of(Revenue.class, CurrencyRevenue.class, MonthlyAmount.class)) {
            Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).forEach(names::add);
        }

        assertThat(names).containsExactlyInAnyOrder("source", "paidOrders", "currencies", "currency", "current",
                "previous", "changePct", "paidOrders", "monthly", "month", "amount", "partial");
        assertThat(names).noneMatch(name -> name.toLowerCase().contains("tenant"))
                .noneMatch(name -> name.toLowerCase().contains("customer"))
                .noneMatch(name -> name.toLowerCase().contains("payment"))
                .noneMatch(name -> name.toLowerCase().contains("license"));
    }

    @Test
    void theSameFacadeDataGivesTheSamePayloadOnEveryCall() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("revenue", sections.revenue(window));

        assertThat(sections.revenue(window)).isEqualTo(first.get("revenue"));
    }
}
