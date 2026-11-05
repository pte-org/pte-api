package com.pte.assessment.internal.mapper;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The pinned rubric version reaches {@link ExamDefinitionResponse} and survives every derived copy. */
class ExamDefinitionRubricPinMappingTest {

    private static final UUID RUBRIC = UUID.randomUUID();

    @Test
    void theMapperCopiesThePinFromTheEntity() {
        assertThat(ExamDefinitionMapper.toResponse(template(RUBRIC)).rubricVersionPublicId()).isEqualTo(RUBRIC);
    }

    @Test
    void aLegacyTemplateWithoutAPinMapsToNull() {
        assertThat(ExamDefinitionMapper.toResponse(template(null)).rubricVersionPublicId()).isNull();
    }

    @Test
    void derivedResponsesKeepThePin() {
        ExamDefinitionResponse response = ExamDefinitionMapper.toResponse(template(RUBRIC));

        assertThat(response.withAuthorName("Author").rubricVersionPublicId()).isEqualTo(RUBRIC);
        assertThat(response.withStandardLabel(false, List.of("deviation")).rubricVersionPublicId())
                .isEqualTo(RUBRIC);
    }

    private static ExamDefinition template(UUID pin) {
        ExamDefinition template = new ExamDefinition();
        template.setPublicId(UUID.randomUUID());
        template.setCode("PTE_FULL");
        template.setVersion(1);
        template.setName("Full");
        template.setStatus(ExamDefinitionStatus.ACTIVE);
        template.setRubricVersionPublicId(pin);
        return template;
    }
}
