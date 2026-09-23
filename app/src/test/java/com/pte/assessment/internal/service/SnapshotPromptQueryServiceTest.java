package com.pte.assessment.internal.service;

import com.pte.assessment.domain.SnapshotItem;
import com.pte.assessment.dto.response.ExaminerQuestionPromptView;
import com.pte.assessment.internal.repository.SnapshotItemRepository;
import com.pte.itembank.domain.enums.PteSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotPromptQueryServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private SnapshotItemRepository snapshotItemRepository;

    private SnapshotPromptQueryService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotPromptQueryService(snapshotItemRepository, jsonMapper);
    }

    @Test
    void mapsTenantPinnedPromptButNeverProjectsReferenceOrCorrectAnswerFields() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SnapshotItem item = new SnapshotItem();
        item.setPublicId(itemId);
        item.setTaskTypeKey("FILL_BLANKS_READING");
        item.setSection(PteSection.READING);
        item.setOrderIndex(4);
        item.setTitle("Vocabulary context");
        item.setPromptText("Choose the best words for each gap.");
        item.setReferenceAnswerText("private reference answer");
        item.setCorrectAnswerText("private correct answer");
        item.setOptionsJson("[{\"text\":\"choice A\",\"correct\":true,\"orderIndex\":1,"
                + "\"blankIndex\":0,\"correctGapIndex\":7},{\"text\":\"choice B\",\"correct\":false,"
                + "\"orderIndex\":2,\"blankIndex\":0,\"correctGapIndex\":7}]");
        when(snapshotItemRepository.findAllForTenant(List.of(itemId), tenantId)).thenReturn(List.of(item));

        Map<UUID, ExaminerQuestionPromptView> result = service.findForExaminer(List.of(itemId), tenantId);

        assertThat(result).containsKey(itemId);
        ExaminerQuestionPromptView prompt = result.get(itemId);
        assertThat(prompt.section()).isEqualTo("READING");
        assertThat(prompt.taskType()).isEqualTo("FILL_BLANKS_READING");
        assertThat(prompt.options()).extracting(ExaminerQuestionPromptView.Option::text)
                .containsExactly("choice A", "choice B");
        assertThat(prompt.options()).extracting(ExaminerQuestionPromptView.Option::blankIndex)
                .containsExactly(0, 0);
        String serialized = jsonMapper.writeValueAsString(prompt);
        assertThat(serialized).doesNotContain("correct", "correctGapIndex", "referenceAnswerText", "correctAnswerText",
                "private reference answer", "private correct answer");
        verify(snapshotItemRepository).findAllForTenant(List.of(itemId), tenantId);
    }

    @Test
    void nullItemOrTenantDoesNotQueryAcrossTenantBoundary() {
        assertThat(service.findForExaminer(null, UUID.randomUUID())).isEmpty();
        assertThat(service.findForExaminer(List.of(UUID.randomUUID()), null)).isEmpty();
        assertThat(service.findForExaminer(List.of(), UUID.randomUUID())).isEmpty();
        assertThat(service.findForExaminer(java.util.Arrays.asList((UUID) null), UUID.randomUUID())).isEmpty();

        verifyNoPromptLookup();
    }

    @Test
    void malformedPinnedOptionsFailClosedInsteadOfReturningAnIncompletePrompt() {
        SnapshotItem item = new SnapshotItem();
        item.setTaskTypeCode("WRITE_ESSAY");
        item.setSection(PteSection.WRITING);
        item.setOptionsJson("not-json");
        UUID itemId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        item.setPublicId(itemId);
        when(snapshotItemRepository.findAllForTenant(List.of(itemId), tenantId)).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.findForExaminer(List.of(itemId), tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Pinned question options could not be decoded");
    }

    private void verifyNoPromptLookup() {
        verify(snapshotItemRepository, never()).findAllForTenant(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
