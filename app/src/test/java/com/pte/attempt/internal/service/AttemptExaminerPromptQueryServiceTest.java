package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptExaminerPromptQueryServiceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private ExamAttemptRepository attemptRepository;

    private AttemptExaminerPromptQueryService service;

    @BeforeEach
    void setUp() {
        service = new AttemptExaminerPromptQueryService(attemptRepository, jsonMapper);
    }

    @Test
    void readsPromptFromAttemptPinnedItemRatherThanAssessmentSnapshotId() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID pinnedItemId = UUID.randomUUID();
        ExamAttempt attempt = attempt(tenantId, sessionId, attemptId);
        PinnedItem item = new PinnedItem();
        item.setPublicId(pinnedItemId);
        item.setOrderIndex(2);
        item.setSection("SPEAKING");
        item.setTaskTypeKey("READ_ALOUD");
        item.setTitle("Read this aloud");
        item.setPromptText("The platform supports daily English practice.");
        item.setCorrectAnswerText("secret answer");
        item.setOptionsJson("[{\"text\":\"choice\",\"correct\":true,\"orderIndex\":1,"
                + "\"blankIndex\":null,\"correctGapIndex\":null}]");
        attempt.getPinnedSnapshot().addItem(item);
        when(attemptRepository.findWithPinnedByPublicId(attemptId)).thenReturn(Optional.of(attempt));

        Map<UUID, AttemptExaminerPromptView> result = service.findForExaminer(
                attemptId, sessionId, tenantId, List.of(pinnedItemId, pinnedItemId));

        assertThat(result).containsOnlyKeys(pinnedItemId);
        AttemptExaminerPromptView prompt = result.get(pinnedItemId);
        assertThat(prompt.taskType()).isEqualTo("READ_ALOUD");
        assertThat(prompt.promptText()).contains("daily English");
        assertThat(prompt.options()).extracting(AttemptExaminerPromptView.Option::text)
                .containsExactly("choice");
        assertThat(jsonMapper.writeValueAsString(prompt)).doesNotContain("secret answer", "correctGapIndex");
    }

    @Test
    void rejectsMismatchedTenantOrSessionBeforeReturningPinnedContent() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID pinnedItemId = UUID.randomUUID();
        ExamAttempt attempt = attempt(tenantId, sessionId, attemptId);
        PinnedItem item = new PinnedItem();
        item.setPublicId(pinnedItemId);
        attempt.getPinnedSnapshot().addItem(item);
        when(attemptRepository.findWithPinnedByPublicId(attemptId)).thenReturn(Optional.of(attempt));

        assertThat(service.findForExaminer(attemptId, UUID.randomUUID(), tenantId, List.of(pinnedItemId))).isEmpty();
        assertThat(service.findForExaminer(attemptId, sessionId, UUID.randomUUID(), List.of(pinnedItemId))).isEmpty();
    }

    @Test
    void doesNotQueryWhenRequestIsIncomplete() {
        assertThat(service.findForExaminer(null, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID())))
                .isEmpty();
        assertThat(service.findForExaminer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of()))
                .isEmpty();

        verify(attemptRepository, never()).findWithPinnedByPublicId(org.mockito.ArgumentMatchers.any());
    }

    private ExamAttempt attempt(UUID tenantId, UUID sessionId, UUID attemptId) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptId);
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(sessionId);
        PinnedExamSnapshot snapshot = new PinnedExamSnapshot();
        snapshot.setTenantId(tenantId);
        snapshot.setSourceSessionPublicId(sessionId);
        snapshot.setAttempt(attempt);
        attempt.setPinnedSnapshot(snapshot);
        return attempt;
    }
}
