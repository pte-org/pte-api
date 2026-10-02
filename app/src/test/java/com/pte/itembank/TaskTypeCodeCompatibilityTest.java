package com.pte.itembank;

import com.pte.itembank.domain.enums.PteTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTypeCodeCompatibilityTest {

    @Test
    void canonicalize_mapsOnlyTheKnownPreV40Aliases() {
        assertThat(TaskTypeCodeCompatibility.canonicalize("FILL_BLANKS_READING_WRITING"))
                .isEqualTo("FILL_IN_THE_BLANKS_DROPDOWN");
        assertThat(TaskTypeCodeCompatibility.canonicalize("FILL_BLANKS_READING"))
                .isEqualTo("FILL_IN_THE_BLANKS_DRAG_AND_DROP");
        assertThat(TaskTypeCodeCompatibility.canonicalize("FILL_BLANKS_LISTENING"))
                .isEqualTo("FILL_IN_THE_BLANKS_TYPE_IN");
    }

    @Test
    void parse_acceptsCanonicalAndLegacyValuesForReadCompatibility() {
        assertThat(TaskTypeCodeCompatibility.parse("read_aloud")).isEqualTo(PteTaskType.READ_ALOUD);
        assertThat(TaskTypeCodeCompatibility.parse("FILL_BLANKS_LISTENING"))
                .isEqualTo(PteTaskType.FILL_IN_THE_BLANKS_TYPE_IN);
    }

    @Test
    void newCatalogWritesRejectLegacyAliasesAndUnknownCodes() {
        assertThatThrownBy(() -> TaskTypeCodeCompatibility.requireCanonicalCode("FILL_BLANKS_READING"))
                .hasMessage("INVALID_QUESTION_TYPE");
        assertThatThrownBy(() -> TaskTypeCodeCompatibility.requireCanonicalCode("NOT_A_TASK"))
                .hasMessage("INVALID_QUESTION_TYPE");
    }
}
