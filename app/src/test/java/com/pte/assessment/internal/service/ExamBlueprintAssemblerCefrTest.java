package com.pte.assessment.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pte.assessment.dto.response.CefrTargetResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.service.ExamBlueprintAssembler.AssembledBlueprint;
import com.pte.itembank.ExamCandidateService;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.ExamCandidateQuestion;
import com.pte.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExamBlueprintAssemblerCefrTest {

    private static final CurrentUser HOST = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
    private static final List<String> LEVELS = List.of("B1", "B2", "C1", "C2");

    private ItembankService bank;
    private ExamCandidateService candidates;

    @BeforeEach
    void setUp() {
        bank = mock(ItembankService.class);
        when(bank.countPublishedByTaskTypes(anySet())).thenAnswer(invocation -> {
            Set<PteTaskType> requested = invocation.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(type -> counts.put(type, 20L));
            return counts;
        });
        when(bank.publishedQuestionIds(any(PteTaskType.class))).thenAnswer(invocation -> IntStream.range(0, 20)
                .mapToObj(index -> UUID.nameUUIDFromBytes((invocation.getArgument(0).toString() + index)
                        .getBytes(StandardCharsets.UTF_8)))
                .toList());
        candidates = mock(ExamCandidateService.class);
        when(candidates.candidates(anyString())).thenAnswer(invocation -> {
            List<ExamCandidateQuestion> questions = new ArrayList<>();
            for (String level : LEVELS) {
                for (int index = 0; index < 5; index++) {
                    questions.add(new ExamCandidateQuestion(UUID.nameUUIDFromBytes(
                            (invocation.getArgument(0) + level + index).getBytes(StandardCharsets.UTF_8)), level));
                }
            }
            return questions;
        });
    }

    private static ExamDefinitionItemResponse slot(PteTaskType task, PteSection section, int sequence, int count) {
        return new ExamDefinitionItemResponse(task.name(), section.name(), sequence, count, count, 0, 60,
                "OBJECTIVE", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static ExamDefinitionResponse template(List<CefrTargetResponse> targets) {
        return new ExamDefinitionResponse(UUID.randomUUID(), "STANDARD", 1, "Cefr", "ACTIVE", null, List.of(
                slot(PteTaskType.MC_READING_SINGLE, PteSection.READING, 1, 4),
                slot(PteTaskType.MC_LISTENING_SINGLE, PteSection.LISTENING, 2, 4)),
                "STANDARD_PTE", null, null, null, null, null, null, null, null, List.of(), targets);
    }

    private static List<CefrTargetResponse> evenMix() {
        return LEVELS.stream().map(level -> new CefrTargetResponse(level, 25)).toList();
    }

    private AssembledBlueprint assemble(ExamDefinitionResponse template, ExamCandidateService candidateService) {
        ExamBlueprintAssembler assembler = new ExamBlueprintAssembler(bank, null, candidateService);
        return assembler.assembleWithReport("Exam", 7L, HOST, template, null);
    }

    @Test
    void aTemplateWithACefrMixDrawsEachSkillToTheMixAndReportsIt() {
        AssembledBlueprint assembled = assemble(template(evenMix()), candidates);

        var cefr = assembled.report().cefr();
        assertThat(cefr).isNotNull();
        assertThat(cefr.target()).containsEntry("B1", 25).containsEntry("C2", 25);
        assertThat(cefr.actualBySkill().get("READING"))
                .containsEntry("B1", 1).containsEntry("B2", 1).containsEntry("C1", 1).containsEntry("C2", 1);
        assertThat(cefr.actualBySkill().get("LISTENING"))
                .containsEntry("B1", 1).containsEntry("B2", 1).containsEntry("C1", 1).containsEntry("C2", 1);
        assertThat(cefr.fallbacks()).isEmpty();
    }

    @Test
    void theBlueprintHoldsOneQuestionPerNeedWithNoRepeats() {
        AssembledBlueprint assembled = assemble(template(evenMix()), candidates);

        var ids = assembled.blueprint().getItems().stream().map(item -> item.getQuestionPublicId()).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(assembled.blueprint().getItems().stream()
                .filter(item -> item.getSection() == PteSection.READING).count()).isEqualTo(4L);
    }

    @Test
    void aTemplateWithoutACefrMixDoesNotStratify() {
        AssembledBlueprint assembled = assemble(template(List.of()), candidates);

        assertThat(assembled.report().cefr()).isNull();
    }

    @Test
    void withoutACandidateServiceTheDrawFallsBackToTheUnstratifiedOne() {
        AssembledBlueprint assembled = assemble(template(evenMix()), null);

        assertThat(assembled.report().cefr()).isNull();
        assertThat(assembled.blueprint().getItems()).isNotEmpty();
    }
}
