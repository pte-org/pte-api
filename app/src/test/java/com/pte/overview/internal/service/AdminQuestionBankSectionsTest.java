package com.pte.overview.internal.service;

import com.pte.itembank.ItembankOverviewService.QuestionBankCounts;
import com.pte.overview.internal.AdminDashboardFixtures;
import com.pte.overview.internal.dto.response.AdminSectionData.QuestionBank;
import com.pte.overview.internal.dto.response.TrendValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.pte.overview.internal.AdminDashboardFixtures.NOW;
import static com.pte.overview.internal.AdminDashboardFixtures.PREVIOUS_START;
import static com.pte.overview.internal.AdminDashboardFixtures.WINDOW_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Payload composition of the question bank admin section over the mocked item bank facade. */
class AdminQuestionBankSectionsTest {

    private AdminDashboardFixtures fx;
    private AdminQuestionBankSections sections;
    private DashboardWindow window;

    @BeforeEach
    void setUp() {
        fx = new AdminDashboardFixtures().stubHappyPath();
        sections = fx.questionBankSections();
        window = DashboardWindow.of(30, NOW);
    }

    private static Map<String, Long> counts(Object... keyAndCount) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < keyAndCount.length; i += 2) {
            map.put((String) keyAndCount[i], ((Number) keyAndCount[i + 1]).longValue());
        }
        return map;
    }

    @Test
    void theSectionMapsTheFacadeCountsOneToOneWithEveryEnumKey() {
        QuestionBank bank = sections.questionBank(window);

        assertThat(bank.total()).isEqualTo(120);
        assertThat(bank.byStatus()).containsExactly(Map.entry("DRAFT", 20L), Map.entry("PENDING_APPROVAL", 10L),
                Map.entry("APPROVED", 80L), Map.entry("ARCHIVED", 10L));
        assertThat(bank.bySection()).containsOnlyKeys("LISTENING", "SPEAKING", "READING", "WRITING");
        assertThat(bank.bySection().values()).containsOnly(30L);
        assertThat(bank.byPool()).containsExactly(Map.entry("EXAM", 100L), Map.entry("PRACTICE", 20L));
        assertThat(bank.approvedByPool()).containsExactly(Map.entry("EXAM", 70L), Map.entry("PRACTICE", 10L));
    }

    @Test
    void createdInWindowIsATrendOfTheCurrentAgainstThePreviousWindow() {
        QuestionBank bank = sections.questionBank(window);

        assertThat(bank.createdInWindow()).isEqualTo(new TrendValue(12, 8, 50.0));
        verify(fx.itembank).countCreatedBetween(WINDOW_START, NOW);
        verify(fx.itembank).countCreatedBetween(PREVIOUS_START, WINDOW_START);
    }

    @Test
    void aZeroPreviousWindowGivesANullChangeNotAnInventedPercentage() {
        when(fx.itembank.countCreatedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(0L);

        assertThat(sections.questionBank(window).createdInWindow()).isEqualTo(new TrendValue(12, 0, null));
    }

    @Test
    void aShrinkingBankKeepsTheNegativeSign() {
        when(fx.itembank.countCreatedBetween(WINDOW_START, NOW)).thenReturn(2L);
        when(fx.itembank.countCreatedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(8L);

        assertThat(sections.questionBank(window).createdInWindow()).isEqualTo(new TrendValue(2, 8, -75.0));
    }

    @Test
    void anEmptyBankIsAllZerosWithNoChange() {
        reset(fx.itembank);
        when(fx.itembank.counts()).thenReturn(new QuestionBankCounts(0,
                counts("DRAFT", 0, "PENDING_APPROVAL", 0, "APPROVED", 0, "ARCHIVED", 0),
                counts("LISTENING", 0, "SPEAKING", 0, "READING", 0, "WRITING", 0),
                counts("EXAM", 0, "PRACTICE", 0), counts("EXAM", 0, "PRACTICE", 0)));
        when(fx.itembank.countCreatedBetween(WINDOW_START, NOW)).thenReturn(0L);
        when(fx.itembank.countCreatedBetween(PREVIOUS_START, WINDOW_START)).thenReturn(0L);

        QuestionBank bank = sections.questionBank(window);

        assertThat(bank.total()).isZero();
        assertThat(bank.byStatus().values()).containsOnly(0L).hasSize(4);
        assertThat(bank.bySection().values()).containsOnly(0L).hasSize(4);
        assertThat(bank.byPool().values()).containsOnly(0L).hasSize(2);
        assertThat(bank.approvedByPool().values()).containsOnly(0L).hasSize(2);
        assertThat(bank.createdInWindow()).isEqualTo(new TrendValue(0, 0, null));
    }

    @Test
    void theSectionReadsOnlyTheItemBankFacade() {
        sections.questionBank(window);

        verify(fx.itembank).counts();
        verify(fx.itembank).countCreatedBetween(WINDOW_START, NOW);
        verify(fx.itembank).countCreatedBetween(PREVIOUS_START, WINDOW_START);
        verifyNoMoreInteractions(fx.itembank);
        verifyNoInteractions(fx.revenueFacade, fx.billing, fx.tenancy, fx.tenancyOverview, fx.identity, fx.attempts,
                fx.support);
    }

    @Test
    void thePayloadHoldsCountsOnlyNoQuestionTextAnswerOrAuthor() {
        List<String> names = Arrays.stream(QuestionBank.class.getRecordComponents()).map(RecordComponent::getName)
                .toList();

        assertThat(names).containsExactly("total", "byStatus", "bySection", "byPool", "approvedByPool",
                "createdInWindow");
        assertThat(Arrays.stream(QuestionBankCounts.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("total", "byStatus", "bySection", "byPool", "approvedByPool");
        // there is no approval-time trend: the bank stores no approval timestamp
        assertThat(names).noneMatch(name -> name.toLowerCase().contains("approvedinwindow"));
    }
}
