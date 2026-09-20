package com.pte.scoretemplate.internal.mapper;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;

public final class ScoreTemplateMapper {

    private ScoreTemplateMapper() {
    }

    public static ScoreTemplateResponse toResponse(ScoreTemplate template) {
        return new ScoreTemplateResponse(
                template.getPublicId(),
                template.getCode(),
                template.getVersion(),
                template.getName(),
                template.getStatus().name(),
                template.getItems().stream().map(ScoreTemplateMapper::toItemResponse).toList());
    }

    private static ScoreTemplateItemResponse toItemResponse(ScoreTemplateItem item) {
        return new ScoreTemplateItemResponse(
                item.getTaskType(),
                item.getSection(),
                item.getSequence(),
                item.getMinCount(),
                item.getMaxCount(),
                item.getPrepSeconds(),
                item.getResponseSeconds(),
                item.getScoringMethod().name(),
                item.getOverallWeight(),
                item.getSpeakingWeight(),
                item.getWritingWeight(),
                item.getReadingWeight(),
                item.getListeningWeight());
    }
}
