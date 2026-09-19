package com.pte.itembank;

import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.request.CreateQuestionTypeRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.SupportedQuestionTypeResponse;
import com.pte.itembank.internal.exception.InvalidQuestionTypeException;
import com.pte.itembank.internal.exception.QuestionTypeCodeAlreadyUsedException;
import com.pte.itembank.internal.exception.QuestionTypeNotFoundException;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Source of truth for the question-type catalog used by authoring clients and
 * task-specific validation.
 */
@Service
public class QuestionTypeService {

    private final QuestionTypeRepository repository;

    public QuestionTypeService(QuestionTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<QuestionTypeResponse> list(boolean activeOnly) {
        List<QuestionTypeDefinition> definitions = activeOnly
                ? repository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc()
                : repository.findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();
        return definitions.stream().map(QuestionTypeMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuestionTypeResponse get(UUID publicId) {
        return QuestionTypeMapper.toResponse(findByPublicId(publicId));
    }

    /**
     * Lists standard task codes that an administrator can add to the persisted
     * catalog through the UI. This keeps the compatibility enum on the server
     * instead of duplicating it in a frontend bundle.
     */
    @Transactional(readOnly = true)
    public List<SupportedQuestionTypeResponse> listSupported() {
        return Arrays.stream(PteTaskType.values())
                .map(taskType -> new SupportedQuestionTypeResponse(
                        taskType.name(), taskType.getSection(), taskType.isScored()))
                .toList();
    }

    /**
     * Creates or restores one standard PTE task type.
     *
     * <p>Question rows store {@link PteTaskType} as an enum-backed integration
     * key, so accepting arbitrary catalog codes here would create types that
     * the question bank cannot author. The server therefore owns the canonical
     * section/scoring/authoring metadata for every supported code.
     */
    @Transactional
    public QuestionTypeResponse create(CreateQuestionTypeRequest request) {
        String code = normalizeCode(request.code());
        PteTaskType taskType = parseTaskType(code);
        validateSection(taskType, request.section());

        QuestionTypeDefinition definition = repository.findByCode(code)
                .map(existing -> {
                    if (!existing.isDeleted()) {
                        throw new QuestionTypeCodeAlreadyUsedException();
                    }
                    return existing;
                })
                .orElseGet(QuestionTypeDefinition::new);

        definition.setDeleted(false);
        definition.setCode(code);
        definition.setDisplayName(request.displayName().trim());
        definition.setShortName(request.shortName().trim());
        definition.setSection(taskType.getSection());
        definition.setScored(taskType.isScored());
        definition.setActive(request.active());
        definition.setDisplayOrder(request.displayOrder());
        applyCanonicalRequirements(definition, taskType);

        return QuestionTypeMapper.toResponse(repository.save(definition));
    }

    @Transactional
    public QuestionTypeResponse update(UUID publicId, UpdateQuestionTypeRequest request) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        definition.setDisplayName(request.displayName().trim());
        definition.setShortName(request.shortName().trim());
        definition.setDisplayOrder(request.displayOrder());
        definition.setActive(request.active());
        definition.setRequiresAudioPrompt(request.requiresAudioPrompt());
        definition.setRequiresImagePrompt(request.requiresImagePrompt());
        definition.setRequiresPromptText(request.requiresPromptText());
        definition.setRequiresOptions(request.requiresOptions());
        definition.setRequiresCorrectAnswer(request.requiresCorrectAnswer());
        definition.setRequiresWordCount(request.requiresWordCount());
        definition.setRequiresSingleCorrectOption(request.requiresSingleCorrectOption());
        definition.setUsesOptionOrderAsCorrectPosition(request.usesOptionOrderAsCorrectPosition());
        return QuestionTypeMapper.toResponse(repository.save(definition));
    }

    /** Soft-deletes a type so existing question rows keep their stable FK key. */
    @Transactional
    public void delete(UUID publicId) {
        QuestionTypeDefinition definition = findByPublicId(publicId);
        definition.setActive(false);
        definition.setDeleted(true);
        repository.save(definition);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionTypeDefinition> findDefinitionByCode(String code) {
        // A deleted catalog row must remain readable by validation/delivery so
        // existing questions keep their authored behavior and stable code.
        return repository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String code) {
        return repository.findByCodeAndDeletedFalse(code).map(QuestionTypeDefinition::isActive).orElse(false);
    }

    private QuestionTypeDefinition findByPublicId(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(QuestionTypeNotFoundException::new);
    }

    private PteTaskType parseTaskType(String code) {
        try {
            return PteTaskType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new InvalidQuestionTypeException();
        }
    }

    private void validateSection(PteTaskType taskType, String section) {
        if (section == null) {
            throw new InvalidQuestionTypeException();
        }
        final PteSection requestedSection;
        try {
            requestedSection = PteSection.valueOf(section.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidQuestionTypeException();
        }
        if (taskType.getSection() != requestedSection) {
            throw new InvalidQuestionTypeException();
        }
    }

    private void applyCanonicalRequirements(QuestionTypeDefinition definition, PteTaskType taskType) {
        definition.setRequiresAudioPrompt(taskType.requiresAudioPrompt());
        definition.setRequiresImagePrompt(taskType.requiresImagePrompt());
        definition.setRequiresPromptText(taskType.requiresPromptText());
        definition.setRequiresOptions(taskType.requiresOptions());
        definition.setRequiresCorrectAnswer(taskType.requiresCorrectAnswer());
        definition.setRequiresWordCount(taskType.requiresWordCount());
        definition.setRequiresSingleCorrectOption(
                taskType == PteTaskType.MC_READING_SINGLE || taskType == PteTaskType.MC_LISTENING_SINGLE);
        definition.setUsesOptionOrderAsCorrectPosition(taskType == PteTaskType.RE_ORDER_PARAGRAPHS);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
