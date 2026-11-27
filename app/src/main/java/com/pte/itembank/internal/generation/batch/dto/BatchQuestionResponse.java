package com.pte.itembank.internal.generation.batch.dto;

import com.pte.itembank.dto.response.GenerationMetaResponse;
import com.pte.itembank.dto.response.OptionResponse;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One question slot of a batch for its result cards: where it stands and, once saved, a light preview of the
 * question (the start of its prompt and options, its media, its review). Every preview field is null until the
 * question is saved, and for a slot that failed or was cancelled.
 *
 * @param reviewPassed criteria the reviewer passed, out of {@code reviewTotal}; null when the question has no review
 */
public record BatchQuestionResponse(
        UUID jobPublicId, int batchSeq, int itemIndex, String taskType, String cefrLevel, String stage,
        String failureCode, UUID questionPublicId, String section, String topic, String promptExcerpt,
        List<String> options, Integer correctOptionIndex, UUID audioRef, UUID imageRef, Double audioSeconds,
        String voice, String chartType, Integer words, Integer reviewPassed, Integer reviewTotal) {

    public static BatchQuestionResponse of(QuestionGenerationJob job, QuestionGenerationJobItem item,
                                           QuestionResponse question) {
        String cefr = job.getCefrLevel() == null ? null : job.getCefrLevel().name();
        if (question == null) {
            return new BatchQuestionResponse(job.getPublicId(), job.getBatchSeq(), item.getItemIndex(),
                    job.getTaskTypeKey(), cefr, item.getStage().name(), item.getFailureCode(),
                    item.getQuestionPublicId(), null, null, null, List.of(), null, null, null, null, null, null,
                    null, null, null);
        }
        List<OptionResponse> ordered = question.options() == null ? List.of() : question.options().stream()
                .sorted(Comparator.comparingInt(OptionResponse::orderIndex))
                .limit(QuestionGenerationConstants.BATCH_PREVIEW_MAX_OPTIONS).toList();
        Integer correct = null;
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).correct()) {
                correct = index;
                break;
            }
        }
        GenerationMetaResponse meta = question.generationMeta();
        GenerationMetaResponse.GenerationMediaResponse media = meta == null ? null : meta.media();
        GenerationMetaResponse.ReviewVerdictResponse review = meta == null ? null : meta.review();
        Integer total = review == null || review.checks() == null ? null : review.checks().size();
        Integer passed = total == null ? null
                : (int) review.checks().values().stream().filter(GenerationMetaResponse.CheckResultResponse::ok)
                        .count();
        return new BatchQuestionResponse(job.getPublicId(), job.getBatchSeq(), item.getItemIndex(),
                job.getTaskTypeKey(), question.cefrLevel() == null ? cefr : question.cefrLevel(),
                item.getStage().name(), item.getFailureCode(), question.publicId(), question.section(),
                meta == null ? null : meta.topic(),
                excerpt(question.promptText(), QuestionGenerationConstants.BATCH_PREVIEW_PROMPT_CHARS),
                ordered.stream().map(option -> excerpt(option.text(),
                        QuestionGenerationConstants.BATCH_PREVIEW_OPTION_CHARS)).toList(),
                correct, question.audioPromptRef(), question.imagePromptRef(),
                media == null ? null : media.durationSec(), media == null ? null : media.voice(),
                media == null ? null : media.chartType(),
                meta == null || meta.metrics() == null ? null : meta.metrics().words(), passed, total);
    }

    private static String excerpt(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
