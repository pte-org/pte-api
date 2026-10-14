package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.AdminSectionData.SeatUtilisation;
import com.pte.tenancy.TenantSeatLimit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seat utilisation ranking: exact cross-multiplied ordering (never the rounded percentage), seat limits
 * of zero or less are skipped, deterministic tie-breaks and a hard cap on the list size.
 */
class SeatUtilisationRankingTest {

    private static TenantSeatLimit limit(String name, int seats) {
        return new TenantSeatLimit(UUID.randomUUID(), "C-" + name, name, seats);
    }

    private static Map<UUID, Long> students(Object... pairs) {
        Map<UUID, Long> map = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(((TenantSeatLimit) pairs[i]).publicId(), ((Number) pairs[i + 1]).longValue());
        }
        return map;
    }

    private static List<String> names(List<SeatUtilisation> rows) {
        return rows.stream().map(SeatUtilisation::name).toList();
    }

    @Test
    void ordersFullestFirstByTheExactRatio() {
        TenantSeatLimit half = limit("half", 100);
        TenantSeatLimit full = limit("full", 10);
        TenantSeatLimit tenth = limit("tenth", 100);
        TenantSeatLimit overbooked = limit("overbooked", 10);

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(half, tenth, full, overbooked),
                students(half, 50, tenth, 10, full, 10, overbooked, 15), 10);

        assertThat(names(top)).containsExactly("overbooked", "full", "half", "tenth");
        assertThat(top.get(0).utilisationPct()).isEqualTo(150.0);
        assertThat(top.get(1).utilisationPct()).isEqualTo(100.0);
        assertThat(top.get(2).utilisationPct()).isEqualTo(50.0);
        assertThat(top.get(3).utilisationPct()).isEqualTo(10.0);
    }

    @Test
    void ratiosThatRoundToTheSamePercentAreStillOrderedExactly() {
        // 1/3 = 33.333..% and 333/1000 = 33.3%: both display as 33.3 but 1/3 is strictly fuller.
        TenantSeatLimit third = limit("third", 3);
        TenantSeatLimit nearThird = limit("near-third", 1000);

        List<SeatUtilisation> forward = SeatUtilisationRanking.top(List.of(nearThird, third),
                students(third, 1, nearThird, 333), 5);
        List<SeatUtilisation> reversed = SeatUtilisationRanking.top(List.of(third, nearThird),
                students(third, 1, nearThird, 333), 5);

        assertThat(names(forward)).containsExactly("third", "near-third");
        assertThat(names(reversed)).containsExactly("third", "near-third");
        assertThat(forward.get(0).utilisationPct()).isEqualTo(33.3);
        assertThat(forward.get(1).utilisationPct()).isEqualTo(33.3);
    }

    @Test
    void anAlmostEqualRatioIsNotConfusedByIntegerOverflowOrDivision() {
        // Cross-multiplication must stay exact for large seat counts: 2_000_000_000 limits, students just below.
        TenantSeatLimit big = limit("big", 2_000_000_000);
        TenantSeatLimit slightlyLess = limit("slightly-less", 1_999_999_999);

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(slightlyLess, big),
                students(big, 1_999_999_999L, slightlyLess, 1_999_999_999L), 5);

        // 1999999999/2000000000 < 1 == 1999999999/1999999999
        assertThat(names(top)).containsExactly("slightly-less", "big");
    }

    @Test
    void tenantsWithoutAPositiveSeatLimitAreSkipped() {
        TenantSeatLimit zero = limit("zero", 0);
        TenantSeatLimit negative = limit("negative", -5);
        TenantSeatLimit normal = limit("normal", 10);

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(zero, negative, normal),
                students(zero, 50, negative, 50, normal, 1), 5);

        assertThat(names(top)).containsExactly("normal");
    }

    @Test
    void aTenantWithoutAGroupedCountHasZeroStudentsAndZeroPercent() {
        TenantSeatLimit empty = limit("empty", 40);

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(empty), Map.of(), 5);

        assertThat(top).singleElement().satisfies(row -> {
            assertThat(row.students()).isZero();
            assertThat(row.seatLimit()).isEqualTo(40);
            assertThat(row.utilisationPct()).isEqualTo(0.0);
            assertThat(row.tenantId()).isEqualTo(empty.publicId());
            assertThat(row.code()).isEqualTo("C-empty");
        });
    }

    @Test
    void studentsOfTenantsMissingFromTheSeatLimitsNeverAppear() {
        TenantSeatLimit real = limit("real", 10);
        Map<UUID, Long> counts = students(real, 5);
        counts.put(UUID.randomUUID(), 1_000L);

        assertThat(names(SeatUtilisationRanking.top(List.of(real), counts, 5))).containsExactly("real");
    }

    @Test
    void equalRatioPrefersMoreStudentsThenTheNameCaseInsensitively() {
        TenantSeatLimit small = limit("small", 4); // 2/4
        TenantSeatLimit large = limit("large", 100); // 50/100
        TenantSeatLimit beta = limit("Beta", 100); // 50/100, same size as large
        TenantSeatLimit alpha = limit("alpha", 100); // 50/100, same size as large

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(small, beta, large, alpha),
                students(small, 2, large, 50, beta, 50, alpha, 50), 10);

        assertThat(names(top)).containsExactly("alpha", "Beta", "large", "small");
    }

    @Test
    void completelyTiedRowsKeepTheirInputOrder() {
        TenantSeatLimit first = new TenantSeatLimit(UUID.randomUUID(), "A", "Same", 10);
        TenantSeatLimit second = new TenantSeatLimit(UUID.randomUUID(), "B", "Same", 10);
        TenantSeatLimit third = new TenantSeatLimit(UUID.randomUUID(), "C", "Same", 10);
        Map<UUID, Long> counts = students(first, 5, second, 5, third, 5);

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(first, second, third), counts, 10);
        List<SeatUtilisation> again = SeatUtilisationRanking.top(List.of(first, second, third), counts, 10);

        assertThat(top).extracting(SeatUtilisation::code).containsExactly("A", "B", "C");
        assertThat(again).isEqualTo(top);
    }

    @Test
    void theResultIsCappedAtNKeepingTheFullest() {
        List<TenantSeatLimit> limits = new ArrayList<>();
        Map<UUID, Long> counts = new java.util.HashMap<>();
        for (int i = 1; i <= 12; i++) {
            TenantSeatLimit tenant = limit("t" + i, 100);
            limits.add(tenant);
            counts.put(tenant.publicId(), (long) i);
        }
        Collections.shuffle(limits, new java.util.Random(7));

        List<SeatUtilisation> top = SeatUtilisationRanking.top(limits, counts, 5);

        assertThat(names(top)).containsExactly("t12", "t11", "t10", "t9", "t8");
    }

    @Test
    void aCapLargerThanTheListReturnsEverythingAndAZeroOrNegativeCapReturnsNothing() {
        TenantSeatLimit a = limit("a", 10);
        TenantSeatLimit b = limit("b", 10);
        Map<UUID, Long> counts = students(a, 1, b, 2);

        assertThat(SeatUtilisationRanking.top(List.of(a, b), counts, 100)).hasSize(2);
        assertThat(SeatUtilisationRanking.top(List.of(a, b), counts, 0)).isEmpty();
        assertThat(SeatUtilisationRanking.top(List.of(a, b), counts, -3)).isEmpty();
    }

    @Test
    void anEmptyTenantListIsAnEmptyRanking() {
        assertThat(SeatUtilisationRanking.top(List.of(), Map.of(), 5)).isEmpty();
    }

    @Test
    void percentIsOneDecimalHalfUpAndNotTruncated() {
        TenantSeatLimit twoThirds = limit("two-thirds", 3);
        TenantSeatLimit eighth = limit("eighth", 8); // 1/8 = 12.5 exactly
        TenantSeatLimit sixteenth = limit("sixteenth", 16); // 1/16 = 6.25 -> 6.3 (HALF_UP)

        List<SeatUtilisation> top = SeatUtilisationRanking.top(List.of(twoThirds, eighth, sixteenth),
                students(twoThirds, 2, eighth, 1, sixteenth, 1), 5);

        assertThat(top).extracting(SeatUtilisation::utilisationPct).containsExactly(66.7, 12.5, 6.3);
    }

    @Test
    void theRankingDoesNotMutateItsInputs() {
        TenantSeatLimit a = limit("a", 10);
        TenantSeatLimit b = limit("b", 10);
        List<TenantSeatLimit> limits = new ArrayList<>(List.of(a, b));
        Map<UUID, Long> counts = students(a, 1, b, 9);

        SeatUtilisationRanking.top(limits, counts, 5);

        assertThat(limits).containsExactly(a, b);
        assertThat(counts).hasSize(2);
    }
}
