package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.domain.ExamCefrTarget;
import com.pte.assessment.dto.request.UpdateCefrTargetsRequest;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.itembank.domain.enums.CefrLevel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Rules a CEFR mix must satisfy: known levels, each at most once, whole percents summing to 100. */
public final class CefrTargetRules {

    static final int TOTAL_PERCENT = 100;

    private CefrTargetRules() {
    }

    public static List<ExamCefrTarget> fromRequest(UpdateCefrTargetsRequest request) {
        Set<CefrLevel> seen = EnumSet.noneOf(CefrLevel.class);
        List<ExamCefrTarget> targets = new ArrayList<>();
        int total = 0;
        for (UpdateCefrTargetsRequest.Target target : request.targets()) {
            CefrLevel level = parse(target.level());
            if (!seen.add(level)) {
                throw new ExamDefinitionValidationException(ExamDefinitionConstants.CEFR_TARGET_DUPLICATE_LEVEL + level);
            }
            targets.add(new ExamCefrTarget(level.name(), target.percent()));
            total += target.percent();
        }
        if (total != TOTAL_PERCENT) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.CEFR_TARGET_NOT_100 + total);
        }
        return List.copyOf(targets);
    }

    private static CefrLevel parse(String level) {
        try {
            return CefrLevel.valueOf(level.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.CEFR_TARGET_UNKNOWN_LEVEL + level);
        }
    }
}
