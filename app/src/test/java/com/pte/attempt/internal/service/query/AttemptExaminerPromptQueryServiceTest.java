package com.pte.attempt.internal.service.query;

import com.pte.attempt.internal.service.ExamContentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
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

    @Mock
    private ExamContentService contentService;

    private AttemptExaminerPromptQueryService service;

    @BeforeEach
    void setUp() {
        service = new AttemptExaminerPromptQueryService(attemptRepository, contentService, jsonMapper);
    }

    @Test
    void readsPromptFromTheAttemptsExamVersion() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ExamAttempt attempt = attempt(tenantId, sessionId, attemptId);
        SnapshotContentResponse.Item item = new SnapshotContentResponse.Item(2, "SPEAKING", "READ_ALOUD",
                "Read this aloud", "The platform supports daily English practice.", null, null, null,
                "secret answer", null, null,
                "[{\"text\":\"choice\",\"correct\":true,\"orderIndex\":1,\"blankIndex\":null,"
                        + "\"correctGapIndex\":null}]",
                "READ_ALOUD", "Read this aloud", "READ_ALOUD", null, null, null, null, itemId);
        when(attemptRepository.findByPublicId(attemptId)).thenReturn(Optional.of(attempt));
        when(contentService.contentItems(attempt.getExamVersionPublicId())).thenReturn(Map.of(itemId, item));

        Map<UUID, AttemptExaminerPromptView> result = service.findForExaminer(
                attemptId, sessionId, tenantId, List.of(itemId, itemId));

        assertThat(result).containsOnlyKeys(itemId);
        AttemptExaminerPromptView prompt = result.get(itemId);
        assertThat(prompt.taskType()).isEqualTo("READ_ALOUD");
        assertThat(prompt.promptText()).contains("daily English");
        assertThat(prompt.options()).extracting(AttemptExaminerPromptView.Option::text)
                .containsExactly("choice");
        assertThat(jsonMapper.writeValueAsString(prompt)).doesNotContain("secret answer", "correctGapIndex");
    }

    @Test
    void rejectsMismatchedTenantOrSessionBeforeReturningExamContent() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ExamAttempt attempt = attempt(tenantId, sessionId, attemptId);
        when(attemptRepository.findByPublicId(attemptId)).thenReturn(Optional.of(attempt));

        assertThat(service.findForExaminer(attemptId, UUID.randomUUID(), tenantId, List.of(itemId))).isEmpty();
        assertThat(service.findForExaminer(attemptId, sessionId, UUID.randomUUID(), List.of(itemId))).isEmpty();
        verify(contentService, never()).contentItems(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotQueryWhenRequestIsIncomplete() {
        assertThat(service.findForExaminer(null, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID())))
                .isEmpty();
        assertThat(service.findForExaminer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of()))
                .isEmpty();

        verify(attemptRepository, never()).findByPublicId(org.mockito.ArgumentMatchers.any());
    }

    private ExamAttempt attempt(UUID tenantId, UUID sessionId, UUID attemptId) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptId);
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(sessionId);
        attempt.setExamVersionPublicId(UUID.randomUUID());
        return attempt;
    }
}
