package com.pte.assessment.internal.service;

import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;

/**
 * Position of a template item inside its section. Normally the template's own
 * {@code sequence}, except Summarize Spoken Text, which always opens the
 * Listening section regardless of how the template numbered it.
 */
final class TemplateTaskOrder {

    private TemplateTaskOrder() {
    }

    static int sequenceInSection(PteSection section, String taskTypeKey, int templateSequence) {
        boolean opensListening = section == PteSection.LISTENING
                && PteTaskType.SUMMARIZE_SPOKEN_TEXT.name().equals(taskTypeKey);
        return opensListening ? Integer.MIN_VALUE : templateSequence;
    }
}
