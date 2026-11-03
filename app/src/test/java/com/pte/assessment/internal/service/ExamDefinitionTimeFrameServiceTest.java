package com.pte.assessment.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamTimeFrame;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.dto.request.UpdateExamTimeFrameRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.exception.ExamDefinitionNotDraftException;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.assessment.internal.service.definition.ExamDefinitionAdminService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExamDefinitionTimeFrameServiceTest {

    private static final UpdateExamTimeFrameRequest PEARSON_WINDOW =
            new UpdateExamTimeFrameRequest(4560, 5040, 1380, 1800, 1860, 2340, 8100);

    @Mock
    private ExamDefinitionRepository repository;

    private ExamDefinitionAdminService service;

    @BeforeEach
    void setUp() {
        service = new ExamDefinitionAdminService(repository, null, null, null, null, null, null,
                com.pte.TestClocks.fixed());
    }

    private ExamDefinition template(UUID publicId, ExamDefinitionStatus status) {
        ExamDefinition template = new ExamDefinition();
        template.setPublicId(publicId);
        template.setCode("PTE");
        template.setVersion(1);
        template.setName("PTE");
        template.setStatus(status);
        return template;
    }

    @Test
    void updateTimeFrame_storesTheWindowOnADraft() {
        UUID publicId = UUID.randomUUID();
        when(repository.findWithItemsByPublicIdAndTenantIdIsNull(publicId))
                .thenReturn(Optional.of(template(publicId, ExamDefinitionStatus.DRAFT)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse response = service.updateTimeFrame(publicId, PEARSON_WINDOW, null);

        assertThat(response.timeFrame()).isNotNull();
        assertThat(response.timeFrame().speakingWritingMaxSeconds()).isEqualTo(5040);
        assertThat(response.timeFrame().totalMaxSeconds()).isEqualTo(8100);
    }

    @Test
    void updateTimeFrame_refusesATemplateThatIsNotADraft() {
        UUID publicId = UUID.randomUUID();
        when(repository.findWithItemsByPublicIdAndTenantIdIsNull(publicId))
                .thenReturn(Optional.of(template(publicId, ExamDefinitionStatus.ACTIVE)));

        assertThatThrownBy(() -> service.updateTimeFrame(publicId, PEARSON_WINDOW, null))
                .isInstanceOf(ExamDefinitionNotDraftException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateTimeFrame_refusesAnInconsistentWindow() {
        UUID publicId = UUID.randomUUID();
        when(repository.findWithItemsByPublicIdAndTenantIdIsNull(publicId))
                .thenReturn(Optional.of(template(publicId, ExamDefinitionStatus.DRAFT)));
        UpdateExamTimeFrameRequest inverted = new UpdateExamTimeFrameRequest(5040, 4560, 1380, 1800, 1860, 2340, 8100);

        assertThatThrownBy(() -> service.updateTimeFrame(publicId, inverted, null))
                .isInstanceOf(ExamDefinitionValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void cloneToDraft_copiesTheTimeFrame() {
        UUID sourceId = UUID.randomUUID();
        ExamDefinition source = template(sourceId, ExamDefinitionStatus.ACTIVE);
        source.setTimeFrame(new ExamTimeFrame(4560, 5040, 1380, 1800, 1860, 2340, 8100));
        when(repository.findWithItemsByPublicIdAndTenantIdIsNull(sourceId)).thenReturn(Optional.of(source));
        when(repository.findMaxVersionByCode("PTE")).thenReturn(1);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExamDefinitionResponse clone = service.cloneToDraft(sourceId, null);

        assertThat(clone.timeFrame()).isNotNull();
        assertThat(clone.timeFrame().listeningMaxSeconds()).isEqualTo(2340);
    }
}
