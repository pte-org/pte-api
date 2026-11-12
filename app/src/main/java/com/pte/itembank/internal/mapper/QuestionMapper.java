package com.pte.itembank.internal.mapper;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.dto.response.GenerationMetaResponse;
import com.pte.itembank.dto.response.OptionResponse;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.service.QuestionLifecyclePolicy;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.generation.dto.GenerationMetaDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/** Maps {@link Question} to its response DTO. */
public final class QuestionMapper {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionMapper.class);

    private QuestionMapper() {
    }

    /** Stateless reader for the stored generation_meta JSON; keeps every toResponse call site unchanged. */
    private static final JsonMapper META_READER = JsonMapper.builder().build();

    public static QuestionResponse toResponse(Question question) {
        return toResponse(question, META_READER);
    }

    public static QuestionResponse toResponse(Question question, JsonMapper jsonMapper) {
        List<OptionResponse> options = question.getOptions().stream()
                .map(QuestionMapper::toOption)
                .toList();
        return new QuestionResponse(
                question.getPublicId(),
                question.getPteTaskType() == null ? null : question.getPteTaskType().name(),
                question.getPteTaskType() == null ? question.getTaskTypeSection()
                        : question.getPteTaskType().getSection().name(),
                question.getTenantId(),
                question.getStatus().name(),
                question.getPromptText(),
                question.getAudioPromptRef(),
                question.getImagePromptRef(),
                question.getReferenceAnswerText(),
                question.getCorrectAnswerText(),
                question.getMinWordCount(),
                question.getMaxWordCount(),
                options,
                question.getRevisionGroupPublicId(),
                question.getRevisionNumber(),
                question.getSupersedesPublicId(),
                question.isCurrent(),
                question.getVersion(),
                question.getRejectionReason(),
                question.getTaskTypeKey() == null && question.getPteTaskType() != null
                        ? question.getPteTaskType().name() : question.getTaskTypeKey(),
                QuestionLifecyclePolicy.canDeleteDraft(question),
                QuestionLifecyclePolicy.canArchive(question),
                QuestionLifecyclePolicy.canDeleteDraft(question) ? null : ItembankConstants.QUESTION_DELETE_NOT_ALLOWED,
                question.getPool().name(),
                question.isAvailableForPractice(), question.isAvailableForExam(),
                Boolean.TRUE.equals(question.getEverPublished()) || question.getSupersedesPublicId() != null,
                question.getUpdatedAt(),
                question.getCefrLevel() == null ? null : question.getCefrLevel().name(),
                question.getAuthorUserPublicId(),
                parseGenerationMeta(question.getGenerationMeta(), jsonMapper),
                question.getAnswerHintVi(),
                question.getAnswerHintEn());
    }

    private static GenerationMetaResponse parseGenerationMeta(String generationMetaJson, JsonMapper jsonMapper) {
        if (generationMetaJson == null || jsonMapper == null) {
            return null;
        }
        try {
            GenerationMetaDto dto = jsonMapper.readValue(generationMetaJson, GenerationMetaDto.class);
            GenerationMetaResponse.ReviewVerdictResponse reviewResponse = null;
            if (dto.review() != null) {
                Map<String, GenerationMetaResponse.CheckResultResponse> checksResponse = null;
                if (dto.review().checks() != null) {
                    checksResponse = dto.review().checks().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> new GenerationMetaResponse.CheckResultResponse(e.getValue().ok(), e.getValue().note(),
                                            e.getValue().estimated())));
                }
                reviewResponse = new GenerationMetaResponse.ReviewVerdictResponse(
                        dto.review().verdict(),
                        checksResponse,
                        dto.review().fixSuggestion());
            }
            return new GenerationMetaResponse(
                    dto.schema(),
                    dto.jobPublicId(),
                    dto.model(),
                    dto.promptVersion(),
                    dto.topic(),
                    dto.attempts(),
                    reviewResponse,
                    dto.metrics() == null ? null : new GenerationMetaResponse.GenerationMetricsResponse(
                            dto.metrics().words(),
                            dto.metrics().avgSentenceLength(),
                            dto.metrics().fleschKincaid()),
                    dto.keyPoints(),
                    dto.media() == null ? null : new GenerationMetaResponse.GenerationMediaResponse(
                            dto.media().voice(),
                            dto.media().speed(),
                            dto.media().durationSec(),
                            dto.media().chartType()),
                    false);
        } catch (Exception e) {
            LOG.warn("Failed to parse generation_meta JSON", e);
            return null;
        }
    }

    private static OptionResponse toOption(QuestionOption option) {
        return new OptionResponse(option.getPublicId(), option.getText(), option.isCorrect(), option.getOrderIndex(),
                option.getBlankIndex(), option.getCorrectGapIndex());
    }
}
