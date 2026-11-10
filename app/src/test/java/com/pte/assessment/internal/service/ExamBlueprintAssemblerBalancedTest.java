package com.pte.assessment.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.service.ExamBlueprintAssembler.AssembledBlueprint;
import com.pte.assessment.internal.service.assembly.AssemblyReport;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExamBlueprintAssemblerBalancedTest {

    private static final CurrentUser HOST = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

    private ExamBlueprintAssembler assembler;

    @BeforeEach
    void setUp() {
        ItembankService bank = mock(ItembankService.class);
        when(bank.countPublishedByTaskTypes(anySet())).thenAnswer(invocation -> {
            Set<PteTaskType> requested = invocation.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(type -> counts.put(type, 50L));
            return counts;
        });
        when(bank.publishedQuestionIds(any(PteTaskType.class))).thenAnswer(invocation -> IntStream.range(0, 50)
                .mapToObj(index -> UUID.nameUUIDFromBytes((invocation.getArgument(0).toString() + index)
                        .getBytes(StandardCharsets.UTF_8)))
                .toList());
        assembler = new ExamBlueprintAssembler(bank);
    }

    private static ExamDefinitionItemResponse slot(PteTaskType task, PteSection section, int sequence, int min,
            int max, int responseSeconds) {
        return new ExamDefinitionItemResponse(task.name(), section.name(), sequence, min, max, 0, responseSeconds,
                "OBJECTIVE", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Speaking 100..300 s + Writing 600 s, Reading and Listening 180..540 s each. */
    private static ExamDefinitionResponse template(ExamTimeFrameResponse frame) {
        return new ExamDefinitionResponse(UUID.randomUUID(), "STANDARD", 1, "Balanced", "ACTIVE", null, List.of(
                slot(PteTaskType.READ_ALOUD, PteSection.SPEAKING, 1, 1, 3, 100),
                slot(PteTaskType.WRITE_ESSAY, PteSection.WRITING, 2, 1, 1, 600),
                slot(PteTaskType.MC_READING_SINGLE, PteSection.READING, 3, 2, 6, 90),
                slot(PteTaskType.MC_LISTENING_SINGLE, PteSection.LISTENING, 4, 2, 6, 90)),
                "STANDARD_PTE", null, null, null, null, null, null, frame);
    }

    private AssembledBlueprint assemble(ExamDefinitionResponse template, long seed, Set<String> skills) {
        return assembler.assembleWithReport("Exam", seed, HOST, template, skills);
    }

    @Test
    void aTemplateWithATimeFrameIsPlannedAndTheReportSaysSo() {
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(750, 850, 300, 450, 300, 450, 5000);

        AssembledBlueprint assembled = assemble(template(frame), 11L, null);

        AssemblyReport report = assembled.report();
        assertThat(report.method()).isEqualTo(AssemblyReport.METHOD_BALANCED);
        assertThat(report.algorithmVersion()).isEqualTo(AssessmentConstants.BALANCED_ALGORITHM_VERSION);
        assertThat(report.counts().get("READ_ALOUD")).isEqualTo(2);
        assertThat(report.counts().get("MC_READING_SINGLE")).isBetween(4, 5);
        assertThat(report.counts().get("MC_LISTENING_SINGLE")).isBetween(4, 5);
        assertThat(report.partSeconds()).containsKeys("SPEAKING_WRITING", "READING", "LISTENING");
    }

    @Test
    void theBlueprintHoldsTheCountsTheReportStates() {
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(750, 850, 300, 450, 300, 450, 5000);

        AssembledBlueprint assembled = assemble(template(frame), 5L, null);

        long readingItems = assembled.blueprint().getItems().stream()
                .filter(item -> item.getSection() == PteSection.READING).count();
        assertThat(readingItems).isEqualTo(assembled.report().counts().get("MC_READING_SINGLE").longValue());
    }

    @Test
    void theSameSeedGivesTheSameCounts() {
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(750, 850, 300, 450, 300, 450, 5000);

        assertThat(assemble(template(frame), 99L, null).report().counts())
                .isEqualTo(assemble(template(frame), 99L, null).report().counts());
    }

    @Test
    void aTemplateWithoutATimeFrameKeepsTheIndependentDraw() {
        AssemblyReport report = assemble(template(null), 3L, null).report();

        assertThat(report.method()).isEqualTo(AssemblyReport.METHOD_INDEPENDENT);
        assertThat(report.algorithmVersion()).isEqualTo(AssessmentConstants.DETERMINISTIC_ALGORITHM_VERSION);
        assertThat(report.fallbackReason()).isNull();
    }

    @Test
    void anExamOfOneSkillIsNotPlannedAgainstThePartWindows() {
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(750, 850, 300, 450, 300, 450, 5000);

        AssemblyReport report = assemble(template(frame), 3L, Set.of("READING")).report();

        assertThat(report.method()).isEqualTo(AssemblyReport.METHOD_INDEPENDENT);
        assertThat(report.fallbackReason()).isNull();
    }

    @Test
    void aFrameNoCountsCanReachStillBuildsTheExamAndSaysWhy() {
        ExamTimeFrameResponse impossible = new ExamTimeFrameResponse(90000, 99000, 300, 450, 300, 450, 500000);

        AssembledBlueprint assembled = assemble(template(impossible), 3L, null);

        assertThat(assembled.blueprint().getItems()).isNotEmpty();
        assertThat(assembled.report().method()).isEqualTo(AssemblyReport.METHOD_INDEPENDENT);
        assertThat(assembled.report().fallbackReason()).isEqualTo("TIME_FRAME_INFEASIBLE:SPEAKING_WRITING");
    }
}
