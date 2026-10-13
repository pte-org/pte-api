package com.pte.billing.internal.repository;

import com.pte.billing.BillingRevenueOverviewService;
import com.pte.billing.BillingRevenueOverviewService.CurrencyPaidTotal;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Online revenue behind {@link BillingRevenueOverviewService} on the embedded database: PAID and not-deleted orders
 * only, grouped per currency (never summed across currencies), half-open {@code paidAt} ranges, exact decimal
 * amounts, a consecutive-range series for the monthly chart and the Asia/Ho_Chi_Minh month boundary.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BillingRevenueOverviewQueriesTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
    private static final Instant FROM = NOW.minusSeconds(30L * 24 * 3600);
    private static final Instant PREV_FROM = FROM.minusSeconds(30L * 24 * 3600);

    @Autowired
    private OrderRepository orders;
    @Autowired
    private PlanRepository plans;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private LicenseCodeRepository licenseCodes;
    @Autowired
    private EntityManager em;
    @Autowired
    private EntityManagerFactory emf;

    private BillingRevenueOverviewService service;
    private long orderCode = 4_000_000_000L;

    @BeforeEach
    void setUp() {
        service = new BillingRevenueOverviewService(orders);
    }

    // ---- builders ----

    private Order order(String currency, String amount, OrderStatus status, boolean deleted, Instant paidAt) {
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), ++orderCode, new BigDecimal(amount), currency);
        order.attachPaymentLink("https://pay.example.test/checkout/" + orderCode);
        switch (status) {
            case PAID -> order.markPaid(paidAt);
            case CANCELLED -> order.cancel();
            case EXPIRED -> order.expire();
            case PENDING -> { }
            default -> throw new IllegalArgumentException(status.name());
        }
        order.setDeleted(deleted);
        return orders.saveAndFlush(order);
    }

    private Order paid(String currency, String amount, Instant paidAt) {
        return order(currency, amount, OrderStatus.PAID, false, paidAt);
    }

    private Map<String, CurrencyPaidTotal> byCurrency(List<CurrencyPaidTotal> totals) {
        return totals.stream().collect(java.util.stream.Collectors.toMap(CurrencyPaidTotal::currency, t -> t));
    }

    // ---- guard: the statuses this query logic was designed against ----

    @Test
    void theOrderStatusesAreExactlyTheOnesTheRevenueRuleWasWrittenFor() {
        // Only PAID counts as revenue. If a refund/failed status is ever added, this test forces a decision.
        assertThat(OrderStatus.values()).containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.PAID,
                OrderStatus.CANCELLED, OrderStatus.EXPIRED);
    }

    // ---- per-currency grouping ----

    @Test
    void amountsAreGroupedPerCurrencyAndNeverSummedAcrossCurrencies() {
        paid("VND", "100000.10", FROM.plusSeconds(10));
        paid("VND", "200000.20", FROM.plusSeconds(20));
        paid("USD", "50.00", FROM.plusSeconds(30));
        paid("USD", "25.25", FROM.plusSeconds(40));
        paid("EUR", "9.99", FROM.plusSeconds(50));

        List<CurrencyPaidTotal> totals = service.paidRevenueBetween(FROM, NOW);

        assertThat(totals).extracting(CurrencyPaidTotal::currency).containsExactly("EUR", "USD", "VND");
        Map<String, CurrencyPaidTotal> map = byCurrency(totals);
        assertThat(map.get("VND").amount()).isEqualTo(new BigDecimal("300000.30"));
        assertThat(map.get("VND").paidOrders()).isEqualTo(2);
        assertThat(map.get("USD").amount()).isEqualTo(new BigDecimal("75.25"));
        assertThat(map.get("USD").paidOrders()).isEqualTo(2);
        assertThat(map.get("EUR").amount()).isEqualTo(new BigDecimal("9.99"));
        assertThat(map.get("EUR").paidOrders()).isEqualTo(1);
        // never a cross-currency total: 300000.30 + 75.25 + 9.99 appears nowhere
        assertThat(totals).extracting(CurrencyPaidTotal::amount).doesNotContain(new BigDecimal("300085.54"));
    }

    @Test
    void currenciesComeOutOrderedByCodeWhateverTheInsertionOrder() {
        paid("VND", "1.00", FROM.plusSeconds(1));
        paid("AUD", "1.00", FROM.plusSeconds(2));
        paid("USD", "1.00", FROM.plusSeconds(3));
        paid("EUR", "1.00", FROM.plusSeconds(4));

        assertThat(service.paidRevenueBetween(FROM, NOW)).extracting(CurrencyPaidTotal::currency)
                .containsExactly("AUD", "EUR", "USD", "VND");
    }

    @Test
    void aCurrencyWithoutPaidOrdersInTheRangeIsAbsentNotZero() {
        paid("VND", "10.00", FROM.plusSeconds(1));
        paid("USD", "10.00", PREV_FROM.plusSeconds(1)); // previous window only

        assertThat(service.paidRevenueBetween(FROM, NOW)).extracting(CurrencyPaidTotal::currency)
                .containsExactly("VND");
        assertThat(service.paidRevenueBetween(PREV_FROM, FROM)).extracting(CurrencyPaidTotal::currency)
                .containsExactly("USD");
    }

    @Test
    void anEmptyDatabaseGivesNoCurrencyAtAll() {
        assertThat(service.paidRevenueBetween(FROM, NOW)).isEmpty();
        assertThat(service.paidAmountsPerRange(List.of(PREV_FROM, FROM, NOW))).isEmpty();
    }

    // ---- which orders count ----

    @Test
    void onlyPaidOrdersCountEveryOtherStatusIsExcluded() {
        Instant inside = FROM.plusSeconds(100);
        paid("VND", "100.00", inside);
        order("VND", "1000.00", OrderStatus.PENDING, false, null);
        order("VND", "2000.00", OrderStatus.CANCELLED, false, null);
        order("VND", "4000.00", OrderStatus.EXPIRED, false, null);

        List<CurrencyPaidTotal> totals = service.paidRevenueBetween(FROM, NOW);

        assertThat(totals).singleElement().satisfies(total -> {
            assertThat(total.currency()).isEqualTo("VND");
            assertThat(total.amount()).isEqualTo(new BigDecimal("100.00"));
            assertThat(total.paidOrders()).isEqualTo(1);
        });
    }

    @Test
    void softDeletedPaidOrdersAreNeverCounted() {
        Instant inside = FROM.plusSeconds(100);
        paid("VND", "100.00", inside);
        order("VND", "9999.00", OrderStatus.PAID, true, inside);
        order("USD", "5.00", OrderStatus.PAID, true, inside);

        List<CurrencyPaidTotal> totals = service.paidRevenueBetween(FROM, NOW);

        assertThat(totals).extracting(CurrencyPaidTotal::currency).containsExactly("VND");
        assertThat(totals.get(0).amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(totals.get(0).paidOrders()).isEqualTo(1);
    }

    @Test
    void aPaidOrderWithoutAPaidAtDateIsNeverCounted() {
        Order legacy = paid("VND", "777.00", FROM.plusSeconds(5));
        em.createQuery("update Order o set o.paidAt = null where o.id = :id").setParameter("id", legacy.getId())
                .executeUpdate();
        em.clear();

        assertThat(service.paidRevenueBetween(Instant.EPOCH, NOW.plusSeconds(86_400))).isEmpty();
    }

    @Test
    void orderCreationTimeIsIrrelevantOnlyPaidAtDecidesTheWindow() {
        // created long ago, paid inside the window
        Order order = paid("VND", "50.00", FROM.plusSeconds(60));
        em.createQuery("update Order o set o.createdAt = :c where o.id = :id")
                .setParameter("c", PREV_FROM.minusSeconds(86_400L * 100)).setParameter("id", order.getId())
                .executeUpdate();
        em.clear();

        assertThat(service.paidRevenueBetween(FROM, NOW)).singleElement()
                .satisfies(total -> assertThat(total.amount()).isEqualTo(new BigDecimal("50.00")));
        assertThat(service.paidRevenueBetween(PREV_FROM, FROM)).isEmpty();
    }

    // ---- half-open windows, previous vs current ----

    @Test
    void theRangeIsHalfOpenTheLowerBoundIsIncludedAndTheUpperBoundExcluded() {
        paid("VND", "1.00", FROM); // exactly from: included
        paid("VND", "2.00", FROM.plusNanos(1_000)); // one microsecond after from: included
        paid("VND", "4.00", NOW.minusNanos(1_000)); // one microsecond before to: included
        paid("VND", "8.00", NOW); // exactly to: excluded
        paid("VND", "16.00", FROM.minusNanos(1_000)); // just before from: excluded

        CurrencyPaidTotal vnd = service.paidRevenueBetween(FROM, NOW).get(0);

        assertThat(vnd.amount()).isEqualTo(new BigDecimal("7.00"));
        assertThat(vnd.paidOrders()).isEqualTo(3);
    }

    @Test
    void adjacentWindowsPartitionTheOrdersSoNothingIsCountedTwiceOrLost() {
        paid("VND", "1.00", PREV_FROM); // first instant of the previous window
        paid("VND", "2.00", FROM.minusNanos(1_000)); // last instant before the current window
        paid("VND", "4.00", FROM); // first instant of the current window
        paid("VND", "8.00", NOW.minusNanos(1_000));

        CurrencyPaidTotal previous = service.paidRevenueBetween(PREV_FROM, FROM).get(0);
        CurrencyPaidTotal current = service.paidRevenueBetween(FROM, NOW).get(0);
        CurrencyPaidTotal both = service.paidRevenueBetween(PREV_FROM, NOW).get(0);

        assertThat(previous.amount()).isEqualTo(new BigDecimal("3.00"));
        assertThat(previous.paidOrders()).isEqualTo(2);
        assertThat(current.amount()).isEqualTo(new BigDecimal("12.00"));
        assertThat(current.paidOrders()).isEqualTo(2);
        assertThat(previous.amount().add(current.amount())).isEqualTo(both.amount());
        assertThat(previous.paidOrders() + current.paidOrders()).isEqualTo(both.paidOrders());
    }

    @Test
    void anEmptyOrInvertedRangeCountsNothing() {
        paid("VND", "1.00", FROM.plusSeconds(1));

        assertThat(service.paidRevenueBetween(FROM, FROM)).isEmpty();
        assertThat(service.paidRevenueBetween(NOW, FROM)).isEmpty();
    }

    // ---- exact decimals ----

    @Test
    void sumsAreExactDecimalsNotFloatingPoint() {
        paid("USD", "0.10", FROM.plusSeconds(1));
        paid("USD", "0.20", FROM.plusSeconds(2));
        paid("USD", "0.01", FROM.plusSeconds(3));

        BigDecimal sum = service.paidRevenueBetween(FROM, NOW).get(0).amount();

        assertThat(sum).isEqualTo(new BigDecimal("0.31")); // 0.1 + 0.2 + 0.01 in double is 0.31000000000000005
        assertThat(sum.scale()).isEqualTo(2);
    }

    @Test
    void largeAmountsKeepEveryDigit() {
        paid("VND", "9999999999999999.99", FROM.plusSeconds(1));
        paid("VND", "0.01", FROM.plusSeconds(2));

        assertThat(service.paidRevenueBetween(FROM, NOW).get(0).amount())
                .isEqualTo(new BigDecimal("10000000000000000.00"));
    }

    @Test
    void theAmountScaleIsTheColumnScaleTwoEvenForWholeNumbers() {
        paid("VND", "500000", FROM.plusSeconds(1));
        em.clear();

        BigDecimal amount = service.paidRevenueBetween(FROM, NOW).get(0).amount();

        assertThat(amount).isEqualByComparingTo("500000");
        assertThat(amount.scale()).isEqualTo(2);
    }

    // ---- series over consecutive ranges ----

    private static List<Instant> monthBoundaries(Instant now, ZoneId zone, int months) {
        YearMonth current = YearMonth.from(now.atZone(zone));
        List<Instant> boundaries = new ArrayList<>();
        for (int offset = months - 1; offset >= 0; offset--) {
            boundaries.add(current.minusMonths(offset).atDay(1).atStartOfDay(zone).toInstant());
        }
        boundaries.add(current.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
        return boundaries;
    }

    @Test
    void theSeriesGivesOneAmountPerRangeForEveryCurrencyWithZeroWhereItHasNone() {
        List<Instant> boundaries = monthBoundaries(NOW, VN, 12);
        paid("VND", "100.00", Instant.parse("2025-11-15T05:00:00Z")); // oldest month
        paid("VND", "200.00", Instant.parse("2026-10-02T05:00:00Z")); // current (partial) month
        paid("VND", "50.00", Instant.parse("2026-10-03T05:00:00Z"));
        paid("USD", "9.00", Instant.parse("2026-03-10T05:00:00Z"));

        Map<String, List<BigDecimal>> series = service.paidAmountsPerRange(boundaries);

        assertThat(series.keySet()).containsExactly("USD", "VND");
        assertThat(series.get("VND")).hasSize(12);
        assertThat(series.get("VND").get(0)).isEqualTo(new BigDecimal("100.00"));
        assertThat(series.get("VND").get(11)).isEqualTo(new BigDecimal("250.00"));
        assertThat(series.get("VND").subList(1, 11)).allMatch(amount -> amount.signum() == 0);
        assertThat(series.get("USD")).hasSize(12);
        assertThat(series.get("USD").get(4)).isEqualTo(new BigDecimal("9.00")); // 2026-03 is the 5th month
        assertThat(series.get("USD").stream().filter(amount -> amount.signum() != 0)).hasSize(1);
    }

    @Test
    void aCurrencyThatOnlyHasOlderMonthsIsStillInTheSeries() {
        List<Instant> boundaries = monthBoundaries(NOW, VN, 12);
        paid("GBP", "42.42", Instant.parse("2026-01-20T05:00:00Z"));

        Map<String, List<BigDecimal>> series = service.paidAmountsPerRange(boundaries);

        assertThat(series).containsOnlyKeys("GBP");
        assertThat(series.get("GBP").get(2)).isEqualTo(new BigDecimal("42.42"));
        // and it is absent from both the current and the previous 30-day windows
        assertThat(service.paidRevenueBetween(FROM, NOW)).isEmpty();
        assertThat(service.paidRevenueBetween(PREV_FROM, FROM)).isEmpty();
    }

    @Test
    void theSumOfTheSeriesEqualsTheRevenueOverTheWholeSpanPerCurrency() {
        List<Instant> boundaries = monthBoundaries(NOW, VN, 12);
        paid("VND", "100.00", Instant.parse("2025-12-05T05:00:00Z"));
        paid("VND", "250.50", Instant.parse("2026-06-30T16:59:59Z"));
        paid("VND", "1.25", Instant.parse("2026-06-30T17:00:00Z"));
        paid("USD", "7.00", Instant.parse("2026-10-12T05:00:00Z"));
        order("USD", "1000.00", OrderStatus.PAID, true, Instant.parse("2026-10-12T05:00:00Z")); // deleted

        Map<String, List<BigDecimal>> series = service.paidAmountsPerRange(boundaries);
        Map<String, CurrencyPaidTotal> whole = byCurrency(
                service.paidRevenueBetween(boundaries.get(0), boundaries.get(12)));

        series.forEach((currency, amounts) -> assertThat(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .as(currency).isEqualTo(whole.get(currency).amount()));
        assertThat(series.keySet()).isEqualTo(whole.keySet());
    }

    @Test
    void anOrderPaidAfterTheLastBoundaryOrBeforeTheFirstIsInNoRange() {
        List<Instant> boundaries = monthBoundaries(NOW, VN, 12);
        paid("VND", "1.00", boundaries.get(0).minusSeconds(1));
        paid("VND", "2.00", boundaries.get(12)); // the exclusive end of the last month

        assertThat(service.paidAmountsPerRange(boundaries)).isEmpty();
    }

    @Test
    void fewerThanTwoBoundariesGiveAnEmptySeriesWithoutAnyQuery() {
        paid("VND", "1.00", FROM.plusSeconds(1));
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.paidAmountsPerRange(null)).isEmpty();
        assertThat(service.paidAmountsPerRange(List.of())).isEmpty();
        assertThat(service.paidAmountsPerRange(List.of(FROM))).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void theSeriesMapAndItsListsAreUnmodifiable() {
        paid("VND", "1.00", Instant.parse("2026-10-02T05:00:00Z"));
        Map<String, List<BigDecimal>> series = service.paidAmountsPerRange(monthBoundaries(NOW, VN, 12));

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> series.put("USD", List.of()));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> series.get("VND").set(0, BigDecimal.TEN));
    }

    // ---- the Asia/Ho_Chi_Minh month boundary (instant that is next month in VN but the same month in UTC) ----

    @Test
    void anOrderPaidAtVietnamMidnightOfTheNextMonthLandsInThatMonthNotInTheUtcMonth() {
        // 2026-10-31T17:30Z is 2026-11-01 00:30 in Asia/Ho_Chi_Minh but still 31 October in UTC.
        Instant boundaryPayment = Instant.parse("2026-10-31T17:30:00Z");
        Instant lastOctoberSecond = Instant.parse("2026-10-31T16:59:59Z"); // 23:59:59 VN, still October
        Instant exactNovemberStart = Instant.parse("2026-10-31T17:00:00Z"); // 00:00:00 VN, 1 November
        paid("VND", "1.00", lastOctoberSecond);
        paid("VND", "10.00", exactNovemberStart);
        paid("VND", "100.00", boundaryPayment);
        Instant clockNow = Instant.parse("2026-10-31T17:30:00Z"); // the dashboard moment: already November in VN

        List<Instant> vnBoundaries = monthBoundaries(clockNow, VN, 12);
        List<Instant> utcBoundaries = monthBoundaries(clockNow, ZoneId.of("UTC"), 12);
        List<BigDecimal> vnd = service.paidAmountsPerRange(vnBoundaries).get("VND");
        List<BigDecimal> vndUtc = service.paidAmountsPerRange(utcBoundaries).get("VND");

        // VN: the last bucket is November (labelled 2026-11) and holds the 00:00:00 and 00:30 payments
        assertThat(vnBoundaries.get(11)).isEqualTo(exactNovemberStart);
        assertThat(vnd.get(10)).as("October (VN)").isEqualTo(new BigDecimal("1.00"));
        assertThat(vnd.get(11)).as("November (VN), partial").isEqualTo(new BigDecimal("110.00"));
        // A UTC-based bucketing would have put all three payments in October.
        assertThat(vndUtc.get(11)).as("October (UTC)").isEqualTo(new BigDecimal("111.00"));
        assertThat(vnd.get(11)).isNotEqualTo(vndUtc.get(11));
    }

    @Test
    void theCurrentMonthRangeCoversThePaymentsMadeSoFarAndTheNextMonthIsNotInTheSeries() {
        List<Instant> boundaries = monthBoundaries(NOW, VN, 12); // NOW = 13 Oct
        paid("VND", "5.00", NOW.minusSeconds(60));
        paid("VND", "7.00", Instant.parse("2026-10-31T16:59:59Z")); // a later October payment (outside a real "now")
        paid("VND", "9.00", Instant.parse("2026-10-31T17:00:00Z")); // November: not in any bucket

        List<BigDecimal> vnd = service.paidAmountsPerRange(boundaries).get("VND");

        assertThat(vnd.get(11)).isEqualTo(new BigDecimal("12.00"));
        assertThat(boundaries.get(12)).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
    }

    // ---- no license activations, no customer/payment data ----

    @Test
    void licenseCodeActivationsAndSubscriptionsNeverContributeToRevenue() {
        Plan plan = new Plan();
        plan.setName("Priced plan");
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setPrice(new BigDecimal("999999.00"));
        plan.setCurrency("VND");
        plan.setStatus(PlanStatus.ACTIVE);
        plans.saveAndFlush(plan);
        LicenseCode code = licenseCodes.saveAndFlush(LicenseCode.issue("LIC-" + UUID.randomUUID().toString().substring(0, 8), plan.getPublicId(),
                UUID.randomUUID(), FROM.minusSeconds(3600), null));
        code.markRedeemed(UUID.randomUUID(), FROM.plusSeconds(60));
        licenseCodes.saveAndFlush(code);
        for (ActivationSource source : ActivationSource.values()) {
            Subscription subscription = new Subscription();
            subscription.setTenantId(UUID.randomUUID());
            subscription.setPlanId(plan.getPublicId());
            subscription.setLicenseKey("K-" + UUID.randomUUID().toString().substring(0, 8));
            subscription.setStartsAt(FROM);
            subscription.setExpiresAt(NOW.plusSeconds(86_400L * 100));
            subscription.setMaxStudentsPerSession(30);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setActivationSource(source);
            subscriptions.saveAndFlush(subscription);
        }

        assertThat(service.paidRevenueBetween(FROM, NOW)).isEmpty();
        assertThat(service.paidAmountsPerRange(monthBoundaries(NOW, VN, 12))).isEmpty();
    }

    @Test
    void theResultRowsOnlyHaveACurrencyAnAmountAndACountEvenWhenTheOrderHasAPaymentLinkAndATenant() {
        Order order = paid("VND", "10.00", FROM.plusSeconds(1));
        assertThat(order.getPaymentLinkUrl()).contains("checkout");
        assertThat(order.getTenantId()).isNotNull();

        List<Object[]> rows = orders.sumAmountAndCountByCurrencyPaidBetween(OrderStatus.PAID, FROM, NOW);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).hasSize(3);
        assertThat(rows.get(0)[0]).isInstanceOf(String.class).isEqualTo("VND");
        assertThat(rows.get(0)[1]).isInstanceOf(BigDecimal.class);
        assertThat(rows.get(0)[2]).isInstanceOf(Long.class);
        assertThat(Arrays.stream(CurrencyPaidTotal.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("currency", "amount", "paidOrders");
    }

    // ---- statement and entity counts ----

    @Test
    void oneWindowIsOneGroupedStatementWithNoEntityLoaded() {
        for (int i = 0; i < 20; i++) {
            paid(i % 2 == 0 ? "VND" : "USD", "1.00", FROM.plusSeconds(i + 1));
        }
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        service.paidRevenueBetween(FROM, NOW);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    @Test
    void theTwelveMonthSeriesIsTwelveGroupedStatementsOneRangeEachAndNoEntityLoaded() {
        for (int i = 0; i < 12; i++) {
            paid("VND", "1.00", monthBoundaries(NOW, VN, 12).get(i).plusSeconds(60));
        }
        em.clear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        service.paidAmountsPerRange(monthBoundaries(NOW, VN, 12));

        assertThat(statistics.getPrepareStatementCount()).as("one grouped statement per month").isEqualTo(12);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }
}
