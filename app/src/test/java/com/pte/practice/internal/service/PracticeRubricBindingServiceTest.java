package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.rubric.RubricPublishedProjection;
import com.pte.rubric.RubricService;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.rubric.internal.service.RubricCanonicalizer;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeRubricBindingServiceTest {

    @Mock
    private RubricService rubricService;

    @Test
    void pinPublishedRubricStoresImmutableProvenanceOnSessionItem() {
        PracticeRubricBindingService service = new PracticeRubricBindingService(rubricService);
        UUID rubricId = UUID.randomUUID();
        when(rubricService.resolvePublishedForTask("WRITE_ESSAY"))
                .thenReturn(new RubricPublishedProjection(rubricId, "PTEPREP.PRACTICE.WRITE_ESSAY",
                        "WRITE_ESSAY", 3, "hash-3",
                        "{\"taskTypeKey\":\"WRITE_ESSAY\",\"runtime\":{\"profileKey\":\"PTE.WRITE_ESSAY\",\"profileVersion\":1,\"behaviorKey\":\"TEXT_RESPONSE\",\"rendererKey\":\"WRITE_ESSAY_V1\",\"screenKey\":\"WRITE_ESSAY_V1\",\"answerSchemaVersion\":1,\"scoringProfileKey\":\"AI_TEXT\",\"scoringProfileVersion\":1,\"requiredClientCapabilities\":[\"TEXT_INPUT\"],\"contractVersion\":1,\"scoringMode\":\"SCORED\",\"minSupportedAppVersion\":\"1.0.0\",\"authoringContractKey\":\"PTE.WRITE_ESSAY_AUTHORING\",\"authoringContractVersion\":1},\"criteria\":[]}",
                        new RubricCanonicalizer(JsonMapper.builder().build()).runtimeCompatibilityHash(TaskRuntimeProfileRegistry.descriptorFor("WRITE_ESSAY"))));
        when(rubricService.runtimeCompatibilityHashForTask("WRITE_ESSAY"))
                .thenReturn(new RubricCanonicalizer(JsonMapper.builder().build())
                        .runtimeCompatibilityHash(TaskRuntimeProfileRegistry.descriptorFor("WRITE_ESSAY")));
        PracticeSessionItem item = new PracticeSessionItem();
        item.setRendererKey("WRITE_ESSAY_V1");
        item.setContractVersion(1);
        item.setAnswerSchemaVersion(1);

        service.pin(item, "WRITE_ESSAY");

        assertThat(item.getRubricRevisionPublicId()).isEqualTo(rubricId);
        assertThat(item.getRubricRevisionNumber()).isEqualTo(3);
        assertThat(item.getRubricCanonicalHash()).isEqualTo("hash-3");
        assertThat(item.getRubricSnapshotJson()).contains("criteria");
    }

    @Test
    void pinRejectsRendererThatThePracticeClientCannotRender() {
        PracticeSessionItem item = new PracticeSessionItem();
        item.setRendererKey("REPEAT_SENTENCE_V1");

        assertThatThrownBy(() -> new PracticeRubricBindingService(rubricService).pin(item, "REPEAT_SENTENCE"))
                .isInstanceOf(PracticeSessionException.class)
                .hasMessage(PracticeConstants.PRACTICE_CONTENT_NOT_READY);
    }
}
