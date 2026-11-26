package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.generation.batch.dto.BatchQuestionResponse;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every question slot of a batch with a preview of the saved ones, for the batch's result cards. Four queries
 * (batch, children, items, saved questions with their options) whatever the batch size.
 */
@Service
public class BatchQuestionsQueryService {

    private final QuestionGenerationBatchQueryService batches;
    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final QuestionRepository questions;

    public BatchQuestionsQueryService(QuestionGenerationBatchQueryService batches, GenerationJobRepository jobs,
                                      GenerationJobItemRepository items, QuestionRepository questions) {
        this.batches = batches;
        this.jobs = jobs;
        this.items = items;
        this.questions = questions;
    }

    /** In run order, then item order; a 404 for a batch the caller may not reach. */
    @Transactional(readOnly = true)
    public List<BatchQuestionResponse> list(UUID batchPublicId, CurrentUser caller) {
        Long batchId = batches.require(batchPublicId, caller).getId();
        Map<Long, QuestionGenerationJob> children = jobs.findByBatchIdOrderByBatchSeqAsc(batchId).stream()
                .collect(Collectors.toMap(QuestionGenerationJob::getId, Function.identity()));
        if (children.isEmpty()) {
            return List.of();
        }
        List<QuestionGenerationJobItem> slots = items.findByJobIdInOrderByItemIndexAsc(children.keySet());
        List<UUID> saved = slots.stream().map(QuestionGenerationJobItem::getQuestionPublicId)
                .filter(Objects::nonNull).toList();
        Map<UUID, QuestionResponse> previews = saved.isEmpty() ? Map.of()
                : questions.findWithOptionsByPublicIdIn(saved).stream()
                        .collect(Collectors.toMap(Question::getPublicId, QuestionMapper::toResponse));
        return slots.stream()
                .sorted(Comparator.comparing((QuestionGenerationJobItem slot) ->
                                children.get(slot.getJobId()).getBatchSeq())
                        .thenComparingInt(QuestionGenerationJobItem::getItemIndex))
                .map(slot -> BatchQuestionResponse.of(children.get(slot.getJobId()), slot,
                        slot.getQuestionPublicId() == null ? null : previews.get(slot.getQuestionPublicId())))
                .toList();
    }
}
