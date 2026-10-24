package com.pte.assessment;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.exception.NoActiveExamDefinitionException;
import com.pte.assessment.internal.exception.ExamDefinitionNotFoundException;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The only door assessment/attempt/scoring/reporting use to reach
 * scoretemplate (see plan.md). Read-only, no PLATFORM_ADMIN gate — those
 * modules are trusted application callers, not end users.
 */
@ExtendWith(MockitoExtension.class)
class ExamDefinitionServiceTest {

    @Mock
    private ExamDefinitionRepository repository;

    private ExamDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new ExamDefinitionService(repository);
    }

    @Test
    void getActive_noActiveTemplate_throws() {
        when(repository.findWithItemsByStatus(ExamDefinitionStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive()).isInstanceOf(NoActiveExamDefinitionException.class);
    }

    @Test
    void getActive_returnsActiveTemplate() {
        ExamDefinition active = new ExamDefinition();
        active.setPublicId(UUID.randomUUID());
        active.setCode("APEUNI_V5");
        active.setVersion(1);
        active.setName("APEUni V5");
        active.setStatus(ExamDefinitionStatus.ACTIVE);
        when(repository.findWithItemsByStatus(ExamDefinitionStatus.ACTIVE)).thenReturn(Optional.of(active));

        ExamDefinitionResponse response = service.getActive();

        assertThat(response.code()).isEqualTo("APEUNI_V5");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getByPublicId_unknownTemplate_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByPublicId(publicId)).isInstanceOf(ExamDefinitionNotFoundException.class);
    }

    @Test
    void getByPublicId_returnsEvenIfRetired_noStatusFilter() {
        UUID publicId = UUID.randomUUID();
        ExamDefinition retired = new ExamDefinition();
        retired.setPublicId(publicId);
        retired.setCode("APEUNI_V5");
        retired.setVersion(1);
        retired.setName("APEUni V5");
        retired.setStatus(ExamDefinitionStatus.RETIRED);
        when(repository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(retired));

        ExamDefinitionResponse response = service.getByPublicId(publicId);

        assertThat(response.status()).isEqualTo("RETIRED");
    }
}
