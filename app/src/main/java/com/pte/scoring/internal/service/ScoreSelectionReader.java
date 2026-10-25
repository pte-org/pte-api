package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.scoring.domain.enums.ScoreSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads which score source the host chose for each answer. The choice lives on the answer in {@code attempt};
 * scoring only decides what may be chosen and reads the result back here.
 */
@Component
public class ScoreSelectionReader {

    private final AttemptService attemptService;

    public ScoreSelectionReader(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    /** The chosen source of one answer: absent source means the host has not chosen yet. */
    public record Selection(ScoreSource source, Instant selectedAt, UUID selectedByPublicId,
                            UUID evaluationPublicId) {
    }

    /** Answers without a choice are simply missing from the result. */
    public Map<UUID, Selection> forSession(UUID sessionPublicId, UUID tenantId) {
        return attemptService.getScoreSelections(sessionPublicId, tenantId).stream()
                .collect(Collectors.toMap(view -> view.answerPublicId(),
                        view -> new Selection(ScoreSource.valueOf(view.source()), view.selectedAt(),
                                view.selectedByPublicId(), view.evaluationPublicId()),
                        (first, second) -> first));
    }

    public static ScoreSource sourceOf(Map<UUID, Selection> selections, UUID answerPublicId) {
        Selection selection = selections.get(answerPublicId);
        return selection == null ? null : selection.source();
    }
}
