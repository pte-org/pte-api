package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamBlueprintAssemblerOrderTest {

    @Test
    void seededGenerationKeepsPartOneTogetherAndAssignsContinuousIndices() {
        ItembankService bank = mock(ItembankService.class);
        when(bank.countPublishedByTaskTypes(anySet())).thenAnswer(invocation -> {
            Set<PteTaskType> requested = invocation.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(type -> counts.put(type, 1L));
            return counts;
        });
        when(bank.publishedQuestionIds(any(PteTaskType.class))).thenAnswer(invocation ->
                List.of(UUID.nameUUIDFromBytes(invocation.getArgument(0).toString()
                        .getBytes(StandardCharsets.UTF_8))));
        ExamDefinitionResponse template = new ExamDefinitionResponse(UUID.randomUUID(), "STANDARD", 1,
                "Part ordering", "ACTIVE", List.of(
                        slot(PteTaskType.MC_LISTENING_SINGLE, PteSection.LISTENING, 22),
                        slot(PteTaskType.MC_READING_SINGLE, PteSection.READING, 14),
                        slot(PteTaskType.WRITE_ESSAY, PteSection.WRITING, 9),
                        slot(PteTaskType.SUMMARIZE_WRITTEN_TEXT, PteSection.WRITING, 8),
                        slot(PteTaskType.RESPOND_TO_A_SITUATION, PteSection.SPEAKING, 7)));
        ExamBlueprintAssembler assembler = new ExamBlueprintAssembler(bank);
        CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
        List<BlueprintItem> items = assembler.assembleDeterministicBlueprint("Exam", template.publicId(),
                42L, host, template, null).getItems();

        assertThat(items).extracting(BlueprintItem::getSection).containsExactly(
                PteSection.SPEAKING, PteSection.SPEAKING, PteSection.WRITING,
                PteSection.WRITING, PteSection.READING, PteSection.LISTENING);
        assertThat(items).extracting(BlueprintItem::getOrderIndex).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(items.get(2).getQuestionPublicId()).isEqualTo(questionId(PteTaskType.SUMMARIZE_WRITTEN_TEXT));
        assertThat(items.get(3).getQuestionPublicId()).isEqualTo(questionId(PteTaskType.WRITE_ESSAY));

        List<BlueprintItem> subset = assembler.assembleDeterministicBlueprint("Writing and Reading",
                template.publicId(), 42L, host, template, Set.of("READING", "WRITING")).getItems();
        assertThat(subset).extracting(BlueprintItem::getSection)
                .containsExactly(PteSection.WRITING, PteSection.WRITING, PteSection.READING);
        assertThat(subset).extracting(BlueprintItem::getOrderIndex).containsExactly(0, 1, 2);
    }

    private static UUID questionId(PteTaskType type) {
        return UUID.nameUUIDFromBytes(type.name().getBytes(StandardCharsets.UTF_8));
    }

    private static ExamDefinitionItemResponse slot(PteTaskType task, PteSection section, int sequence) {
        return new ExamDefinitionItemResponse(task.name(), section.name(), sequence, 1, 1,
                0, 30, "OBJECTIVE", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
