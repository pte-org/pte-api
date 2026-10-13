package com.pte.overview.internal.service;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class MonthBucketsTest {

    private static final ZoneId VN = ZoneId.of(OverviewConstants.BUCKET_ZONE);
    private static final int MONTHS = OverviewConstants.SERIES_MONTHS;

    private static List<Long> counts(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    private static List<String> labels(List<MonthlyPoint> points) {
        return points.stream().map(MonthlyPoint::month).toList();
    }

    @Test
    void zoneConstantIsHoChiMinh() {
        assertThat(OverviewConstants.BUCKET_ZONE).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void instantThatIsNextMonthInVietnamButSameMonthInUtcBelongsToTheVietnamMonth() {
        // 2026-10-31T17:30Z is 2026-11-01 00:30 in Ho Chi Minh but still October in UTC.
        Instant now = Instant.parse("2026-10-31T17:30:00Z");

        List<MonthlyPoint> points = MonthBuckets.toPoints(now, VN, counts(MONTHS));
        List<Instant> boundaries = MonthBuckets.boundaries(now, VN, MONTHS);

        assertThat(points.get(MONTHS - 1).month()).isEqualTo("2026-11");
        assertThat(points.get(0).month()).isEqualTo("2025-12");
        // The current (November, VN) bucket starts at 2026-11-01 00:00 +07 = 2026-10-31T17:00Z...
        assertThat(boundaries.get(MONTHS - 1)).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        // ...and ends at 2026-12-01 00:00 +07 = 2026-11-30T17:00Z.
        assertThat(boundaries.get(MONTHS)).isEqualTo(Instant.parse("2026-11-30T17:00:00Z"));
        assertThat(boundaries.get(0)).isEqualTo(Instant.parse("2025-11-30T17:00:00Z"));
        // A UTC-based bucketing would have labelled the last bucket October.
        assertThat(MonthBuckets.toPoints(now, ZoneId.of("UTC"), counts(MONTHS)).get(MONTHS - 1).month())
                .isEqualTo("2026-10");
    }

    @Test
    void lastSecondOfTheVietnamMonthStillBelongsToThatMonth() {
        Instant now = Instant.parse("2026-10-31T16:59:59Z"); // 2026-10-31 23:59:59 +07
        List<Instant> boundaries = MonthBuckets.boundaries(now, VN, MONTHS);

        assertThat(MonthBuckets.toPoints(now, VN, counts(MONTHS)).get(MONTHS - 1).month()).isEqualTo("2026-10");
        assertThat(boundaries.get(MONTHS - 1)).isEqualTo(Instant.parse("2026-09-30T17:00:00Z"));
        assertThat(boundaries.get(MONTHS)).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        assertThat(boundaries.get(0)).isEqualTo(Instant.parse("2025-10-31T17:00:00Z"));
    }

    @Test
    void exactMonthStartInstantBelongsToTheNewMonth() {
        Instant now = Instant.parse("2026-09-30T17:00:00Z"); // exactly 2026-10-01 00:00 +07

        assertThat(MonthBuckets.toPoints(now, VN, counts(MONTHS)).get(MONTHS - 1).month()).isEqualTo("2026-10");
        assertThat(MonthBuckets.boundaries(now, VN, MONTHS).get(MONTHS - 1)).isEqualTo(now);
    }

    @Test
    void producesTwelveEntriesOldestFirstWithOnlyTheCurrentMonthPartial() {
        Instant now = Instant.parse("2026-10-13T03:00:00Z");

        List<MonthlyPoint> points = MonthBuckets.toPoints(now, VN, counts(MONTHS));

        assertThat(points).hasSize(12);
        assertThat(labels(points)).containsExactly("2025-11", "2025-12", "2026-01", "2026-02", "2026-03",
                "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(points).extracting(MonthlyPoint::count).containsExactlyElementsOf(counts(MONTHS));
        assertThat(points).extracting(MonthlyPoint::partial)
                .containsExactly(false, false, false, false, false, false, false, false, false, false, false, true);
    }

    @Test
    void boundariesAreStrictlyAscendingAndOnePerBucketPlusOne() {
        List<Instant> boundaries = MonthBuckets.boundaries(Instant.parse("2026-10-13T03:00:00Z"), VN, MONTHS);

        assertThat(boundaries).hasSize(MONTHS + 1).isSorted();
        for (int i = 1; i < boundaries.size(); i++) {
            assertThat(boundaries.get(i)).isAfter(boundaries.get(i - 1));
        }
        // Every boundary is a Vietnam local midnight on the first of a month.
        for (Instant boundary : boundaries) {
            var local = boundary.atZone(VN);
            assertThat(local.getDayOfMonth()).isEqualTo(1);
            assertThat(local.toLocalTime().toSecondOfDay()).isZero();
        }
    }

    @Test
    void crossingTheYearBoundaryKeepsConsecutiveCalendarMonths() {
        Instant now = Instant.parse("2026-01-15T05:00:00Z");

        assertThat(labels(MonthBuckets.toPoints(now, VN, counts(MONTHS)))).containsExactly(
                "2025-02", "2025-03", "2025-04", "2025-05", "2025-06", "2025-07", "2025-08", "2025-09",
                "2025-10", "2025-11", "2025-12", "2026-01");
    }

    @Test
    void bucketLengthsFollowTheCalendarIncludingLeapFebruary() {
        Instant leap = Instant.parse("2028-03-10T05:00:00Z");
        List<Instant> leapBoundaries = MonthBuckets.boundaries(leap, VN, MONTHS);
        List<String> leapLabels = labels(MonthBuckets.toPoints(leap, VN, counts(MONTHS)));
        int feb2028 = leapLabels.indexOf("2028-02");
        assertThat(Duration.between(leapBoundaries.get(feb2028), leapBoundaries.get(feb2028 + 1)))
                .isEqualTo(Duration.ofDays(29));

        Instant common = Instant.parse("2027-03-10T05:00:00Z");
        List<Instant> commonBoundaries = MonthBuckets.boundaries(common, VN, MONTHS);
        int feb2027 = labels(MonthBuckets.toPoints(common, VN, counts(MONTHS))).indexOf("2027-02");
        assertThat(Duration.between(commonBoundaries.get(feb2027), commonBoundaries.get(feb2027 + 1)))
                .isEqualTo(Duration.ofDays(28));
    }

    @Test
    void toPointsFollowsTheNumberOfCountsAndHandlesEmptyInput() {
        Instant now = Instant.parse("2026-10-13T03:00:00Z");

        List<MonthlyPoint> three = MonthBuckets.toPoints(now, VN, List.of(5L, 0L, 9L));
        assertThat(three).containsExactly(
                new MonthlyPoint("2026-08", 5, false),
                new MonthlyPoint("2026-09", 0, false),
                new MonthlyPoint("2026-10", 9, true));

        assertThat(MonthBuckets.toPoints(now, VN, List.of())).isEmpty();
    }
}
