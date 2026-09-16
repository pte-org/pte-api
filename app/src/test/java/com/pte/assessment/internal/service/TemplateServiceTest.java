package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamTemplate;
import com.pte.assessment.domain.TemplateSection;
import com.pte.assessment.domain.TemplateSlot;
import com.pte.assessment.domain.enums.TemplateStatus;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.dto.request.TemplateRequest;
import com.pte.assessment.internal.dto.request.TemplateSectionRequest;
import com.pte.assessment.internal.dto.request.TemplateSlotRequest;
import com.pte.assessment.internal.dto.response.TemplateFeasibilityResponse;
import com.pte.assessment.internal.dto.response.TemplateResponse;
import com.pte.assessment.internal.exception.TemplateStateException;
import com.pte.assessment.internal.exception.TemplateValidationException;
import com.pte.assessment.internal.repository.ExamTemplateRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private ExamTemplateRepository templateRepository;
    @Mock
    private ItembankService itembankService;

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateRepository, itembankService);
    }

    @Test
    void draftMayBeSavedBeforeItsWeightsReachOneHundred() {
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TemplateRequest request = request(
                section("READING", 90, slot("MC_READING_SINGLE", 1)));

        TemplateResponse response = service.create(request);

        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    void activateRejectsWeightTotalOfNinetyWith422() {
        UUID publicId = UUID.randomUUID();
        ExamTemplate template = template(publicId, TemplateStatus.DRAFT,
                section(PteSection.READING, 90, slot(PteTaskType.MC_READING_SINGLE, 1)));
        when(templateRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.activate(publicId))
                .isInstanceOfSatisfying(TemplateValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(
                            String.format(AssessmentConstants.TEMPLATE_WEIGHT_TOTAL_INVALID, 90));
                });
    }

    @Test
    void activateRejectsSlotPlacedUnderTheWrongSectionWith422() {
        UUID publicId = UUID.randomUUID();
        ExamTemplate template = template(publicId, TemplateStatus.DRAFT,
                section(PteSection.SPEAKING, 100, slot(PteTaskType.WRITE_ESSAY, 1)));
        when(templateRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.activate(publicId))
                .isInstanceOfSatisfying(TemplateValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(String.format(
                            AssessmentConstants.TEMPLATE_SLOT_SECTION_MISMATCH,
                            "WRITE_ESSAY", "WRITING", "SPEAKING"));
                });
    }

    @Test
    void activateAcceptsACompleteFourSectionTemplate() {
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID publicId = UUID.randomUUID();
        ExamTemplate template = template(publicId, TemplateStatus.DRAFT,
                section(PteSection.SPEAKING, 25, slot(PteTaskType.READ_ALOUD, 1)),
                section(PteSection.WRITING, 25, slot(PteTaskType.WRITE_ESSAY, 1)),
                section(PteSection.READING, 25, slot(PteTaskType.MC_READING_SINGLE, 1)),
                section(PteSection.LISTENING, 25, slot(PteTaskType.WRITE_FROM_DICTATION, 1)));
        when(templateRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(template));

        TemplateResponse response = service.activate(publicId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void activeTemplateRejectsStructuralEditButCloneCanBeEdited() {
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID publicId = UUID.randomUUID();
        ExamTemplate active = template(publicId, TemplateStatus.ACTIVE,
                section(PteSection.READING, 100, slot(PteTaskType.MC_READING_SINGLE, 2)));
        when(templateRepository.findByPublicId(publicId)).thenReturn(Optional.of(active));
        when(templateRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(active));

        TemplateRequest structuralEdit = request(
                section("READING", 100, slot("MC_READING_SINGLE", 9)));
        assertThatThrownBy(() -> service.update(publicId, structuralEdit))
                .isInstanceOfSatisfying(TemplateStateException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(AssessmentConstants.TEMPLATE_ACTIVE_STRUCTURE_LOCKED);
                });

        TemplateResponse cloneResponse = service.clone(publicId);
        ArgumentCaptor<ExamTemplate> cloneCaptor = ArgumentCaptor.forClass(ExamTemplate.class);
        verify(templateRepository).save(cloneCaptor.capture());
        ExamTemplate clone = cloneCaptor.getValue();

        assertThat(cloneResponse.status()).isEqualTo("DRAFT");
        assertThat(clone.getStatus()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(clone.getTenantId()).isNull();
        assertThat(clone.getSections()).singleElement().satisfies(section -> {
            assertThat(section.getSlots()).singleElement()
                    .extracting(TemplateSlot::getQuestionCount).isEqualTo(2);
        });

        when(templateRepository.findByPublicIdForUpdate(clone.getPublicId())).thenReturn(Optional.of(clone));
        TemplateResponse edited = service.update(clone.getPublicId(), structuralEdit);

        assertThat(edited.sections()).singleElement().satisfies(section ->
                assertThat(section.slots()).singleElement()
                        .extracting(TemplateResponse.Slot::questionCount).isEqualTo(9));
    }

    @Test
    void activeTemplateAllowsMetadataOnlyEditWhenSectionsAreOmitted() {
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID publicId = UUID.randomUUID();
        ExamTemplate active = template(publicId, TemplateStatus.ACTIVE,
                section(PteSection.READING, 100, slot(PteTaskType.MC_READING_SINGLE, 2)));
        when(templateRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(active));

        TemplateResponse response = service.update(publicId, new TemplateRequest("Renamed", "New description", null));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.sections()).singleElement().satisfies(section ->
                assertThat(section.slots()).singleElement()
                        .extracting(TemplateResponse.Slot::questionCount).isEqualTo(2));
    }

    @Test
    void feasibilityReturnsOnlySlotsWithInsufficientSharedQuestions() {
        UUID publicId = UUID.randomUUID();
        ExamTemplate template = template(publicId, TemplateStatus.DRAFT,
                section(PteSection.READING, 100,
                        slot(PteTaskType.READ_ALOUD, 5),
                        slot(PteTaskType.MC_READING_SINGLE, 2)));
        when(templateRepository.findByPublicId(publicId)).thenReturn(Optional.of(template));
        when(itembankService.countSharedByTaskTypes(any())).thenReturn(Map.of(
                PteTaskType.READ_ALOUD, 3L,
                PteTaskType.MC_READING_SINGLE, 2L));

        TemplateFeasibilityResponse response = service.feasibility(publicId);

        assertThat(response.feasible()).isFalse();
        assertThat(response.missingSlots()).containsExactly(
                new TemplateFeasibilityResponse.MissingSlot("READING", "READ_ALOUD", 5, 3, 2));
    }

    @Test
    void templateSpecExposesOnlyTheActiveTemplateStructure() {
        UUID publicId = UUID.randomUUID();
        ExamTemplate template = template(publicId, TemplateStatus.ACTIVE,
                section(PteSection.READING, 100, slot(PteTaskType.MC_READING_SINGLE, 2)));
        when(templateRepository.findByPublicId(publicId)).thenReturn(Optional.of(template));

        TemplateSpec spec = service.getTemplateSpec(publicId);

        assertThat(spec.templatePublicId()).isEqualTo(publicId);
        assertThat(spec.sections()).singleElement().satisfies(section -> {
            assertThat(section.section()).isEqualTo(PteSection.READING);
            assertThat(section.weightPercent()).isEqualTo(100);
            assertThat(section.slots()).singleElement()
                    .extracting(TemplateSpec.Slot::taskType, TemplateSpec.Slot::questionCount)
                    .containsExactly(PteTaskType.MC_READING_SINGLE, 2);
        });
    }

    private TemplateRequest request(TemplateSectionRequest... sections) {
        return new TemplateRequest("Template", "Description", List.of(sections));
    }

    private TemplateSectionRequest section(String section, int weight, TemplateSlotRequest... slots) {
        return new TemplateSectionRequest(section, weight, 0, List.of(slots));
    }

    private TemplateSlotRequest slot(String taskType, int count) {
        return new TemplateSlotRequest(taskType, count, 0);
    }

    private TemplateSection section(PteSection section, int weight, TemplateSlot... slots) {
        TemplateSection entity = new TemplateSection();
        entity.setSection(section);
        entity.setWeightPercent(weight);
        for (TemplateSlot slot : slots) {
            entity.addSlot(slot);
        }
        return entity;
    }

    private TemplateSlot slot(PteTaskType taskType, int count) {
        TemplateSlot entity = new TemplateSlot();
        entity.setTaskType(taskType);
        entity.setQuestionCount(count);
        return entity;
    }

    private ExamTemplate template(UUID publicId, TemplateStatus status, TemplateSection... sections) {
        ExamTemplate template = new ExamTemplate();
        template.setPublicId(publicId);
        template.setName("Template");
        template.setStatus(status);
        for (TemplateSection section : sections) {
            template.addSection(section);
        }
        return template;
    }
}
