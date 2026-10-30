package com.pte.assessment.internal.service;

import com.pte.itembank.domain.enums.PteSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateTaskOrderTest {
    @Test
    void summarizeSpokenTextStartsListeningEvenWithLegacyTemplateSequence() {
        int summary = TemplateTaskOrder.sequenceInSection(PteSection.LISTENING, "SUMMARIZE_SPOKEN_TEXT", 99);
        int choice = TemplateTaskOrder.sequenceInSection(PteSection.LISTENING, "MC_LISTENING_MULTIPLE", 1);
        assertThat(summary).isLessThan(choice);
    }

    @Test
    void otherTasksKeepTheTemplateSequence() {
        assertThat(TemplateTaskOrder.sequenceInSection(PteSection.READING, "MC_READING_SINGLE", 14)).isEqualTo(14);
        assertThat(TemplateTaskOrder.sequenceInSection(PteSection.WRITING, "WRITE_ESSAY", 9)).isEqualTo(9);
        assertThat(TemplateTaskOrder.sequenceInSection(PteSection.LISTENING, "WRITE_FROM_DICTATION", 22)).isEqualTo(22);
    }
}
