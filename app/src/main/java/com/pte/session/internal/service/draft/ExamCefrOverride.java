package com.pte.session.internal.service.draft;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.session.domain.SessionCefrTarget;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.CreateExamDraftRequest.CefrTargetRequest;
import com.pte.session.internal.exception.draft.ExamDraftConfigurationException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads the CEFR mix a centre sets on an exam: known levels, each once, whole percents summing to 100. */
final class ExamCefrOverride {

    private static final int TOTAL_PERCENT = 100;

    private ExamCefrOverride() {
    }

    /** The mix to store; an empty or missing request means "follow the template" and stores nothing. */
    static List<SessionCefrTarget> parse(List<CefrTargetRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>();
        }
        Set<CefrLevel> seen = EnumSet.noneOf(CefrLevel.class);
        List<SessionCefrTarget> targets = new ArrayList<>();
        int total = 0;
        for (CefrTargetRequest target : requested) {
            CefrLevel level = level(target.level());
            if (!seen.add(level)) {
                throw new ExamDraftConfigurationException(SessionConstants.CEFR_TARGET_DUPLICATE_LEVEL);
            }
            targets.add(new SessionCefrTarget(level.name(), target.percent()));
            total += target.percent();
        }
        if (total != TOTAL_PERCENT) {
            throw new ExamDraftConfigurationException(SessionConstants.CEFR_TARGET_NOT_100);
        }
        return targets;
    }

    private static CefrLevel level(String value) {
        try {
            return CefrLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ExamDraftConfigurationException(SessionConstants.CEFR_TARGET_UNKNOWN_LEVEL);
        }
    }
}
