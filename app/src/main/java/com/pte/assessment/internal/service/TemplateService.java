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
import com.pte.assessment.internal.exception.TemplateNotFoundException;
import com.pte.assessment.internal.exception.TemplateStateException;
import com.pte.assessment.internal.exception.TemplateValidationException;
import com.pte.assessment.internal.mapper.TemplateMapper;
import com.pte.assessment.internal.repository.ExamTemplateRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Platform-owned exam-template catalog and its draft/active/archived lifecycle. */
@Service
public class TemplateService {

    private static final String CLONE_SUFFIX = " (copy)";

    private final ExamTemplateRepository templateRepository;
    private final ItembankService itembankService;

    public TemplateService(ExamTemplateRepository templateRepository, ItembankService itembankService) {
        this.templateRepository = templateRepository;
        this.itembankService = itembankService;
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        ExamTemplate template = new ExamTemplate();
        template.setTenantId(null);
        applyMetadata(template, request);
        applyStructure(template, request.sections());
        return TemplateMapper.toResponse(templateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID publicId) {
        return TemplateMapper.toResponse(find(publicId));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listForAdmin() {
        return templateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TemplateMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listActive() {
        return templateRepository.findByStatusOrderByCreatedAtDesc(TemplateStatus.ACTIVE).stream()
                .map(TemplateMapper::toResponse)
                .toList();
    }

    @Transactional
    public TemplateResponse update(UUID publicId, TemplateRequest request) {
        ExamTemplate template = findForUpdate(publicId);
        if (template.getStatus() == TemplateStatus.ACTIVE) {
            if (request.sections() != null) {
                throw new TemplateStateException(AssessmentConstants.TEMPLATE_ACTIVE_STRUCTURE_LOCKED);
            }
            applyMetadata(template, request);
        } else {
            applyMetadata(template, request);
            applyStructure(template, request.sections());
        }
        return TemplateMapper.toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse activate(UUID publicId) {
        ExamTemplate template = findForUpdate(publicId);
        if (template.getStatus() != TemplateStatus.DRAFT) {
            throw new TemplateStateException(AssessmentConstants.TEMPLATE_MUST_BE_DRAFT_TO_ACTIVATE);
        }
        validateForActivation(template);
        template.setStatus(TemplateStatus.ACTIVE);
        return TemplateMapper.toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse archive(UUID publicId) {
        ExamTemplate template = findForUpdate(publicId);
        if (template.getStatus() == TemplateStatus.ARCHIVED) {
            throw new TemplateStateException(AssessmentConstants.TEMPLATE_ALREADY_ARCHIVED);
        }
        template.archive();
        return TemplateMapper.toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse clone(UUID publicId) {
        ExamTemplate source = find(publicId);
        ExamTemplate clone = new ExamTemplate();
        clone.setName(cloneName(source.getName()));
        clone.setDescription(source.getDescription());
        clone.setTenantId(null);
        clone.setStatus(TemplateStatus.DRAFT);

        source.getSections().forEach(sourceSection -> {
            TemplateSection section = new TemplateSection();
            section.setSection(sourceSection.getSection());
            section.setWeightPercent(sourceSection.getWeightPercent());
            section.setOrderIndex(sourceSection.getOrderIndex());
            sourceSection.getSlots().forEach(sourceSlot -> {
                TemplateSlot slot = new TemplateSlot();
                slot.setTaskType(sourceSlot.getTaskType());
                slot.setQuestionCount(sourceSlot.getQuestionCount());
                slot.setOrderIndex(sourceSlot.getOrderIndex());
                section.addSlot(slot);
            });
            clone.addSection(section);
        });
        return TemplateMapper.toResponse(templateRepository.save(clone));
    }

    /** Public assessment contract used by the Phase 10 resolver. */
    @Transactional(readOnly = true)
    public TemplateSpec getTemplateSpec(UUID publicId) {
        ExamTemplate template = find(publicId);
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw new TemplateNotFoundException();
        }
        return TemplateMapper.toSpec(template);
    }

    @Transactional(readOnly = true)
    public TemplateFeasibilityResponse feasibility(UUID publicId) {
        ExamTemplate template = find(publicId);
        Set<PteTaskType> taskTypes = template.getSections().stream()
                .flatMap(section -> section.getSlots().stream())
                .map(TemplateSlot::getTaskType)
                .collect(java.util.stream.Collectors.toSet());
        Map<PteTaskType, Long> availableByType = new HashMap<>(itembankService.countSharedByTaskTypes(taskTypes));
        List<TemplateFeasibilityResponse.MissingSlot> missingSlots = new ArrayList<>();

        template.getSections().forEach(section -> section.getSlots().forEach(slot -> {
            long available = availableByType.getOrDefault(slot.getTaskType(), 0L);
            if (available < slot.getQuestionCount()) {
                int missing = slot.getQuestionCount() - Math.toIntExact(available);
                missingSlots.add(new TemplateFeasibilityResponse.MissingSlot(
                        section.getSection().name(), slot.getTaskType().name(), slot.getQuestionCount(), available, missing));
            }
        }));
        return new TemplateFeasibilityResponse(template.getPublicId(), missingSlots.isEmpty(), missingSlots);
    }

    private ExamTemplate find(UUID publicId) {
        return templateRepository.findByPublicId(publicId).orElseThrow(TemplateNotFoundException::new);
    }

    private ExamTemplate findForUpdate(UUID publicId) {
        return templateRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(TemplateNotFoundException::new);
    }

    private void applyMetadata(ExamTemplate template, TemplateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new TemplateValidationException(AssessmentConstants.TEMPLATE_NAME_REQUIRED);
        }
        if (request.description() != null && request.description().length() > 255) {
            throw new TemplateValidationException(AssessmentConstants.TEMPLATE_DESCRIPTION_MAX);
        }
        template.setName(request.name());
        template.setDescription(request.description());
        template.setTenantId(null);
    }

    private void applyStructure(ExamTemplate template, List<TemplateSectionRequest> requests) {
        template.getSections().clear();
        if (requests == null) {
            return;
        }
        requests.forEach(sectionRequest -> {
            if (sectionRequest == null) {
                throw new TemplateValidationException(AssessmentConstants.TEMPLATE_SECTION_REQUIRED);
            }
            TemplateSection section = new TemplateSection();
            section.setSection(parseSection(sectionRequest.section()));
            section.setWeightPercent(sectionRequest.weightPercent() == null ? 0 : sectionRequest.weightPercent());
            section.setOrderIndex(sectionRequest.orderIndex());
            if (sectionRequest.slots() != null) {
                sectionRequest.slots().forEach(slotRequest -> section.addSlot(buildSlot(slotRequest)));
            }
            template.addSection(section);
        });
    }

    private TemplateSlot buildSlot(TemplateSlotRequest request) {
        if (request == null) {
            throw new TemplateValidationException(AssessmentConstants.TEMPLATE_SLOT_TASK_TYPE_REQUIRED);
        }
        TemplateSlot slot = new TemplateSlot();
        slot.setTaskType(parseTaskType(request.taskType()));
        slot.setQuestionCount(request.questionCount() == null ? 0 : request.questionCount());
        slot.setOrderIndex(request.orderIndex());
        return slot;
    }

    private void validateForActivation(ExamTemplate template) {
        int totalWeight = template.getSections().stream().mapToInt(TemplateSection::getWeightPercent).sum();
        if (totalWeight != 100) {
            throw new TemplateValidationException(
                    String.format(AssessmentConstants.TEMPLATE_WEIGHT_TOTAL_INVALID, totalWeight));
        }
        template.getSections().forEach(section -> {
            if (section.getWeightPercent() <= 0 || section.getWeightPercent() > 100) {
                throw new TemplateValidationException(String.format(
                        AssessmentConstants.TEMPLATE_SECTION_WEIGHT_INVALID,
                        section.getSection().name(), section.getWeightPercent()));
            }
            if (section.getSlots().isEmpty()) {
                throw new TemplateValidationException(String.format(
                        AssessmentConstants.TEMPLATE_SECTION_SLOTS_REQUIRED, section.getSection().name()));
            }
            section.getSlots().forEach(slot -> {
                if (slot.getTaskType().getSection() != section.getSection()) {
                    throw new TemplateValidationException(String.format(
                            AssessmentConstants.TEMPLATE_SLOT_SECTION_MISMATCH,
                            slot.getTaskType().name(), slot.getTaskType().getSection().name(), section.getSection().name()));
                }
                if (slot.getQuestionCount() <= 0) {
                    throw new TemplateValidationException(String.format(
                            AssessmentConstants.TEMPLATE_SLOT_QUESTION_COUNT_INVALID,
                            slot.getTaskType().name(), slot.getQuestionCount()));
                }
            });
        });
    }

    private PteSection parseSection(String value) {
        try {
            return PteSection.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new TemplateValidationException(String.format(AssessmentConstants.TEMPLATE_SECTION_INVALID, value));
        }
    }

    private PteTaskType parseTaskType(String value) {
        try {
            return PteTaskType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new TemplateValidationException(String.format(AssessmentConstants.TEMPLATE_TASK_TYPE_INVALID, value));
        }
    }

    private String cloneName(String sourceName) {
        if (sourceName.length() + CLONE_SUFFIX.length() <= 255) {
            return sourceName + CLONE_SUFFIX;
        }
        return sourceName.substring(0, 255 - CLONE_SUFFIX.length()) + CLONE_SUFFIX;
    }
}
