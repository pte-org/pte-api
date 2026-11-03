package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.dto.response.CefrTargetResponse;
import com.pte.itembank.domain.enums.CefrLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Turns the CEFR targets carried by a template or an exam into the map the picker uses. */
public final class CefrMix {

    private CefrMix() {
    }

    /** Percent per level; empty when there are no targets, which means "do not stratify". */
    public static Map<CefrLevel, Integer> of(List<CefrTargetResponse> targets) {
        Map<CefrLevel, Integer> mix = new EnumMap<>(CefrLevel.class);
        if (targets != null) {
            targets.forEach(target -> mix.put(CefrLevel.valueOf(target.level()), target.percent()));
        }
        return mix;
    }
}
