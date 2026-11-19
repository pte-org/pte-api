package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.exception.QuestionDeletionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteRequest;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteResponse;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.exception.GenerationBatchNotFoundException;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchDraftDeletionServiceTest {

    private final QuestionGenerationBatchQueryService queries = mock(QuestionGenerationBatchQueryService.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final BatchDraftDeleteExecutor executor = mock(BatchDraftDeleteExecutor.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final BatchDraftDeletionService service =
            new BatchDraftDeletionService(queries, items, questions, executor, audit);
    private final CurrentUser staff = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private final UUID batchId = UUID.randomUUID();
    private final UUID draft = UUID.randomUUID();
    private final UUID approved = UUID.randomUUID();
    private final UUID foreign = UUID.randomUUID();
    private final UUID gone = UUID.randomUUID();
    private final UUID otherAuthor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        when(queries.require(batchId, staff)).thenReturn(batch);
        when(items.findSavedQuestionPublicIdsByBatch(40L)).thenReturn(List.of(draft, approved, gone, otherAuthor));
        doThrow(new QuestionDeletionException()).when(executor).delete(approved, staff);
        doThrow(new QuestionNotFoundException()).when(executor).delete(gone, staff);
        doThrow(new AccessDeniedException("denied")).when(executor).delete(otherAuthor, staff);
    }

    @Test
    void eachQuestionGetsItsOwnOutcomeAndIdsOutsideTheBatchAreUntouched() {
        when(questions.findDraftPublicIdsIn(any())).thenReturn(List.of(otherAuthor));

        BatchDraftDeleteResponse response = service.deleteDrafts(batchId,
                new BatchDraftDeleteRequest(List.of(draft, approved, foreign, gone, otherAuthor)), staff);

        assertThat(response.items()).extracting(BatchDraftDeleteResponse.Item::result)
                .containsExactly("deleted", "skipped_not_draft", "not_in_batch", "not_found", "forbidden");
        assertThat(response.items().get(1).code()).isEqualTo("QUESTION_DELETE_NOT_ALLOWED");
        assertThat(response.processed()).isEqualTo(5);
        assertThat(response.deleted()).isEqualTo(1);
        assertThat(response.remaining()).isEqualTo(1);
        verify(executor, never()).delete(foreign, staff);
        verify(audit).record(eq(staff), eq("QUESTION_GENERATION_BATCH"), eq(batchId.toString()),
                eq("QUESTION_GENERATION_BATCH_DRAFTS_DELETED"), anyString());
    }

    @Test
    void withoutIdsOnlyTheBatchsLiveDraftsAreTargeted() {
        when(questions.findDraftPublicIdsIn(Set.of(draft, approved, gone, otherAuthor)))
                .thenReturn(List.of(draft), List.of());

        BatchDraftDeleteResponse response = service.deleteDrafts(batchId, new BatchDraftDeleteRequest(null), staff);

        assertThat(response.items()).extracting(BatchDraftDeleteResponse.Item::questionPublicId)
                .containsExactly(draft);
        assertThat(response.remaining()).isZero();
        verify(executor, never()).delete(approved, staff);
    }

    @Test
    void aBatchWithoutSavedQuestionsDeletesNothing() {
        when(items.findSavedQuestionPublicIdsByBatch(40L)).thenReturn(List.of());

        BatchDraftDeleteResponse response = service.deleteDrafts(batchId, new BatchDraftDeleteRequest(List.of()),
                staff);

        assertThat(response.processed()).isZero();
        verify(questions, never()).findDraftPublicIdsIn(any());
    }

    @Test
    void anotherWritersBatchIsNotFoundAndNothingIsDeleted() {
        UUID hidden = UUID.randomUUID();
        when(queries.require(hidden, staff)).thenThrow(new GenerationBatchNotFoundException());

        assertThatThrownBy(() -> service.deleteDrafts(hidden, new BatchDraftDeleteRequest(null), staff))
                .isInstanceOf(GenerationBatchNotFoundException.class);
        verify(executor, never()).delete(any(), any());
    }
}
