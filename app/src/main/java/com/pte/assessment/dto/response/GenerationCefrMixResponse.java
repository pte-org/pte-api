package com.pte.assessment.dto.response;

import java.util.List;

/**
 * The CEFR mix of the active exam template, for the "full exam" preset of AI generation: the template it comes from
 * (so the author sees where the split was taken from) and the share of each level, lowest level first.
 */
public record GenerationCefrMixResponse(String templateName, int templateVersion, List<CefrTargetResponse> targets) {
}
