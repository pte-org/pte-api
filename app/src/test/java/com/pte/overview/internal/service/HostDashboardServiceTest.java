package com.pte.overview.internal.service;

import com.pte.identity.IdentityOverviewService.StudentStatusCounts;
import com.pte.enrollment.EnrollmentOverviewService.AcademicCounts;
import com.pte.overview.internal.HostDashboardFixtures;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.DashboardSection;
import com.pte.overview.internal.dto.response.HostDashboardResponse;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import com.pte.overview.internal.dto.response.MonthlySeries;
import com.pte.overview.internal.dto.response.SectionStatus;
import com.pte.overview.internal.exception.InvalidDashboardWindowException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.pte.overview.internal.HostDashboardFixtures.NOW;
import static com.pte.overview.internal.HostDashboardFixtures.OTHER_TENANT;
import static com.pte.overview.internal.HostDashboardFixtures.PREVIOUS_START;
import static com.pte.overview.internal.HostDashboardFixtures.TENANT;
import static com.pte.overview.internal.HostDashboardFixtures.WINDOW_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HostDashboardServiceTest {

    private HostDashboardFixtures fx;
    private HostDashboardService service;

    @BeforeEach
    void setUp() {
        fx = new HostDashboardFixtures().stubHappyPath(TENANT);
        service = fx.service(HostDashboardFixtures.passThroughCache());
    }

    private enum Section { LEARNERS, QUOTA, ACADEMICS, STAFF, EXAMS, ATTEMPTS }

    private static SectionStatus statusOf(HostDashboardResponse response, Section section) {
        return switch (section) {
            case LEARNERS -> response.learners().status();
            case QUOTA -> response.quota().status();
            case ACADEMICS -> response.academics().status();
            case STAFF -> response.staff().status();
            case EXAMS -> response.exams().status();
            case ATTEMPTS -> response.attempts().status();
        };
    }

    @Test
    void composesEverySectionFromTheFacades() {
        HostDashboardResponse response = service.build(TENANT, null);

        assertThat(response.learners().status()).isEqualTo(SectionStatus.OK);
        var learners = response.learners().data();
        assertThat(learners.total()).isEqualTo(100);
        assertThat(learners.active()).isEqualTo(80);
        assertThat(learners.suspended()).isEqualTo(20);
        assertThat(learners.newInWindow().current()).isEqualTo(12);
        assertThat(learners.newInWindow().previous()).isEqualTo(8);
        assertThat(learners.newInWindow().changePct()).isEqualTo(50.0);
        assertThat(learners.activeLearners().current()).isEqualTo(30);
        assertThat(learners.unassigned()).isEqualTo(30); // 100 students - 70 placed in a class

        var quota = response.quota().data();
        assertThat(quota.used()).isEqualTo(100);
        assertThat(quota.limit()).isEqualTo(500);
        assertThat(quota.remaining()).isEqualTo(400);

        var academics = response.academics().data();
        assertThat(academics.organizations()).isEqualTo(3);
        assertThat(academics.programs()).isEqualTo(4);
        assertThat(academics.activeClasses()).isEqualTo(7);

        var staff = response.staff().data();
        assertThat(staff.proctors()).isEqualTo(5);
        assertThat(staff.examiners()).isEqualTo(6);

        var exams = response.exams().data();
        assertThat(exams.byStatus()).containsEntry("DRAFT", 1L).containsEntry("SCHEDULED", 2L);
        assertThat(exams.created().current()).isEqualTo(6);
        assertThat(exams.created().previous()).isEqualTo(3);
        assertThat(exams.created().changePct()).isEqualTo(100.0);
        assertThat(exams.upcoming()).containsExactly(HostDashboardFixtures.UPCOMING);

        var attempts = response.attempts().data();
        assertThat(attempts.submitted().current()).isEqualTo(40);
        assertThat(attempts.submitted().previous()).isEqualTo(50);
        assertThat(attempts.submitted().changePct()).isEqualTo(-20.0);
        assertThat(attempts.inProgressNow()).isEqualTo(4);
    }

    @Test
    void zeroPreviousPeriodGivesNullChangeNotAnInventedPercentage() {
        // fixtures: distinct active students previous period == 0
        var activeLearners = service.build(TENANT, null).learners().data().activeLearners();

        assertThat(activeLearners.current()).isEqualTo(30);
        assertThat(activeLearners.previous()).isZero();
        assertThat(activeLearners.changePct()).isNull();
    }

    @Test
    void metaEchoesWindowFromTheInjectedClockAndTheBucketZone() {
        var meta = service.build(TENANT, null).meta();

        assertThat(meta.windowDays()).isEqualTo(30);
        assertThat(meta.generatedAt()).isEqualTo(NOW);
        assertThat(meta.windowEnd()).isEqualTo(NOW);
        assertThat(meta.windowStart()).isEqualTo(WINDOW_START);
        assertThat(meta.zone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void currentAndPreviousPeriodsAreQueriedWithContiguousHalfOpenRanges() {
        service.build(TENANT, null);

        verify(fx.identity).countStudentsCreatedBetween(TENANT, WINDOW_START, NOW);
        verify(fx.identity).countStudentsCreatedBetween(TENANT, PREVIOUS_START, WINDOW_START);
        verify(fx.sessions).countCreatedBetween(TENANT, WINDOW_START, NOW);
        verify(fx.sessions).countCreatedBetween(TENANT, PREVIOUS_START, WINDOW_START);
        verify(fx.attempts).countSubmitted(TENANT, WINDOW_START, NOW);
        verify(fx.attempts).countSubmitted(TENANT, PREVIOUS_START, WINDOW_START);
        verify(fx.attempts).countDistinctActiveStudents(TENANT, WINDOW_START, NOW);
        verify(fx.attempts).countDistinctActiveStudents(TENANT, PREVIOUS_START, WINDOW_START);
    }

    @Test
    void windowDaysSelectsTheRequestedWindow() {
        var meta = service.build(TENANT, "7").meta();

        assertThat(meta.windowDays()).isEqualTo(7);
        assertThat(meta.windowStart()).isEqualTo(NOW.minus(Duration.ofDays(7)));
        verify(fx.attempts).countSubmitted(TENANT, NOW.minus(Duration.ofDays(7)), NOW);
        verify(fx.attempts).countSubmitted(TENANT, NOW.minus(Duration.ofDays(14)), NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void invalidWindowIsRejectedBeforeAnyFacadeIsTouched() {
        assertThatThrownBy(() -> service.build(TENANT, "15")).isInstanceOf(InvalidDashboardWindowException.class);
        assertThatThrownBy(() -> service.build(TENANT, "abc")).isInstanceOf(InvalidDashboardWindowException.class);

        verifyNoInteractions(fx.identity, fx.enrollment, fx.sessions, fx.attempts, fx.tenancy);
    }

    @Test
    void everyFacadeCallIsScopedToTheCallersTenantOnly() {
        // Another tenant has obviously different numbers; they must never be read.
        when(fx.identity.studentStatusCounts(OTHER_TENANT)).thenReturn(new StudentStatusCounts(9999, 9999, 0));

        var response = service.build(TENANT, null);

        assertThat(response.learners().data().total()).isEqualTo(100);
        int tenantScopedCalls = 0;
        for (Object mock : List.of(fx.identity, fx.enrollment, fx.sessions, fx.attempts, fx.tenancy)) {
            for (Invocation invocation : mockingDetails(mock).getInvocations()) {
                if (invocation.getArguments().length > 0 && invocation.getArgument(0) instanceof UUID tenant) {
                    assertThat(tenant).as(invocation.toString()).isEqualTo(TENANT);
                    tenantScopedCalls++;
                }
            }
        }
        assertThat(tenantScopedCalls).isGreaterThanOrEqualTo(15);
        verify(fx.identity, never()).studentStatusCounts(OTHER_TENANT);
        verify(fx.identity, times(1)).studentStatusCounts(TENANT);
    }

    @Test
    void unassignedLearnersNeverGoNegative() {
        when(fx.enrollment.academicCounts(TENANT)).thenReturn(new AcademicCounts(1, 1, 500));

        assertThat(service.build(TENANT, null).learners().data().unassigned()).isZero();
    }

    @Test
    void coreCountsAreNotCachedOnlyTheMonthlySeriesGoesThroughTheCache() {
        DashboardCache cache = mock(DashboardCache.class);
        when(cache.getOrCompute(anyString(), any(), any(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(3)).get());
        HostDashboardService cached = fx.service(cache);

        cached.build(TENANT, null);
        cached.build(TENANT, null);

        // Fresh after mutations: every core facade is read again on the second request.
        verify(fx.identity, times(2)).studentStatusCounts(TENANT);
        verify(fx.attempts, times(2)).countInProgress(TENANT);
        verify(fx.sessions, times(2)).countByStatus(TENANT);
        // Exactly one cache lookup per request, for the heavy series.
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache, times(2)).getOrCompute(key.capture(), eq(OverviewConstants.HOST_SERIES_TTL),
                eq(MonthlySeries.class), any());
        assertThat(key.getAllValues()).containsOnly("dash:host:v1:" + TENANT + ":30:attempts-monthly");
    }

    @Test
    void cacheKeyChangesWithTenantAndWindow() {
        DashboardCache cache = HostDashboardFixtures.passThroughCache();
        HostDashboardService cached = fx.service(cache);
        fx.stubHappyPath(OTHER_TENANT);

        cached.build(TENANT, "30");
        cached.build(TENANT, "7");
        cached.build(OTHER_TENANT, "30");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache, times(3)).getOrCompute(key.capture(), any(), any(), any());
        assertThat(key.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void cacheHitSkipsTheHeavyRangeQueriesButStillComputesEverythingElse() {
        DashboardCache cache = mock(DashboardCache.class);
        List<MonthlyPoint> cachedPoints = List.of(new MonthlyPoint("2026-10", 77, true));
        when(cache.getOrCompute(anyString(), any(), any(), any())).thenReturn(new MonthlySeries(cachedPoints));

        var response = fx.service(cache).build(TENANT, null);

        assertThat(response.attempts().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.attempts().data().monthlySubmitted()).isEqualTo(cachedPoints);
        verify(fx.attempts, never()).countSubmittedPerRange(any(), any());
        assertThat(response.learners().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void monthlySeriesIsTwelveMonthsOldestFirstEndingWithThePartialCurrentVietnamMonth() {
        var series = service.build(TENANT, null).attempts().data().monthlySubmitted();

        assertThat(series).hasSize(12);
        assertThat(series.get(0).month()).isEqualTo("2025-11");
        assertThat(series.get(11).month()).isEqualTo("2026-10");
        assertThat(series.get(11).partial()).isTrue();
        assertThat(series.subList(0, 11)).allMatch(point -> !point.partial());
        assertThat(series).extracting(MonthlyPoint::count).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Instant>> boundaries = ArgumentCaptor.forClass(List.class);
        verify(fx.attempts).countSubmittedPerRange(eq(TENANT), boundaries.capture());
        assertThat(boundaries.getValue()).hasSize(13)
                .isEqualTo(MonthBuckets.boundaries(NOW, ZoneId.of("Asia/Ho_Chi_Minh"), 12));
    }

    @ParameterizedTest
    @EnumSource(Section.class)
    void aSingleFailingSectionIsReportedAsErrorWhileTheOthersStayOk(Section failing) {
        RuntimeException failure = new IllegalStateException("simulated outage: secret-detail");
        switch (failing) {
            case LEARNERS -> when(fx.identity.studentStatusCounts(TENANT)).thenThrow(failure);
            case QUOTA -> when(fx.tenancy.getStudentQuota(TENANT)).thenThrow(failure);
            case ACADEMICS -> when(fx.tenancy.countOrganizations(TENANT)).thenThrow(failure);
            case STAFF -> when(fx.identity.activeStaffCounts(TENANT)).thenThrow(failure);
            case EXAMS -> when(fx.sessions.countByStatus(TENANT)).thenThrow(failure);
            case ATTEMPTS -> when(fx.attempts.countInProgress(TENANT)).thenThrow(failure);
        }

        HostDashboardResponse response = service.build(TENANT, null);

        for (Section section : Section.values()) {
            assertThat(statusOf(response, section))
                    .as("section %s when %s fails", section, failing)
                    .isEqualTo(section == failing ? SectionStatus.ERROR : SectionStatus.OK);
        }
        assertThat(response.meta()).isNotNull();
        assertThat(response.toString()).doesNotContain("secret-detail");
    }

    @Test
    void errorSectionCarriesNoData() {
        when(fx.attempts.countInProgress(TENANT)).thenThrow(new RuntimeException("x"));

        DashboardSection<?> attempts = service.build(TENANT, null).attempts();

        assertThat(attempts.status()).isEqualTo(SectionStatus.ERROR);
        assertThat(attempts.data()).isNull();
    }

    @Test
    void aDomainExceptionInsideASectionIsAlsoIsolated() {
        when(fx.tenancy.getStudentQuota(TENANT)).thenThrow(new TenantNotFoundException());

        HostDashboardResponse response = service.build(TENANT, null);

        assertThat(response.quota().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.learners().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void sharedEnrollmentFailureDegradesExactlyTheTwoSectionsThatUseIt() {
        when(fx.enrollment.academicCounts(TENANT)).thenThrow(new RuntimeException("enrollment down"));

        HostDashboardResponse response = service.build(TENANT, null);

        assertThat(response.learners().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.academics().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.quota().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.staff().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.exams().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.attempts().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void monthlySeriesQueryFailureErrorsOnlyTheAttemptsSection() {
        when(fx.attempts.countSubmittedPerRange(eq(TENANT), any())).thenThrow(new RuntimeException("slow query timeout"));

        HostDashboardResponse response = service.build(TENANT, null);

        assertThat(response.attempts().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.learners().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.exams().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void everySectionFailingStillReturnsAResponseInsteadOfThrowing() {
        RuntimeException failure = new RuntimeException("all down");
        when(fx.identity.studentStatusCounts(TENANT)).thenThrow(failure);
        when(fx.tenancy.getStudentQuota(TENANT)).thenThrow(failure);
        when(fx.tenancy.countOrganizations(TENANT)).thenThrow(failure);
        when(fx.identity.activeStaffCounts(TENANT)).thenThrow(failure);
        when(fx.sessions.countByStatus(TENANT)).thenThrow(failure);
        when(fx.attempts.countInProgress(TENANT)).thenThrow(failure);
        when(fx.enrollment.academicCounts(TENANT)).thenThrow(failure);
        when(fx.attempts.countSubmitted(eq(TENANT), any(), any())).thenThrow(failure);

        HostDashboardResponse response = service.build(TENANT, null);

        for (Section section : Section.values()) {
            assertThat(statusOf(response, section)).isEqualTo(SectionStatus.ERROR);
        }
        assertThat(response.meta().windowDays()).isEqualTo(30);
        verify(fx.identity, atLeastOnce()).studentStatusCounts(TENANT);
    }
}
