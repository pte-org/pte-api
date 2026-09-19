package com.pte.itembank;

import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.request.ImportQuestionTypesFromScoreTemplateRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.internal.exception.QuestionTypeNotFoundException;
import com.pte.itembank.internal.exception.QuestionTypeImportException;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public Optional<QuestionTypeDefinition> findDefinitionByCode(String code) {
        return repository.findByCodeAndDeletedFalse(code);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String code) {
        return repository.findByCodeAndDeletedFalse(code).map(QuestionTypeDefinition::isActive).orElse(false);
    }

    /**
     * Imports task types found in an exported score template. Existing catalog
     * rows are left untouched so an author can curate their metadata in the
     * Question Types screen; only missing rows are created.
     */
    @Transactional
    public List<QuestionTypeResponse> importFromScoreTemplate(
            ImportQuestionTypesFromScoreTemplateRequest request) {
        Map<String, ImportQuestionTypesFromScoreTemplateRequest.Item> uniqueItems = new LinkedHashMap<>();
        request.items().stream()
                .sorted(Comparator.comparingInt(ImportQuestionTypesFromScoreTemplateRequest.Item::sequence))
                .forEach(item -> uniqueItems.putIfAbsent(normalizeCode(item.taskType()), item));

        int nextDisplayOrder = repository.findMaxDisplayOrder() + 1;
        for (Map.Entry<String, ImportQuestionTypesFromScoreTemplateRequest.Item> entry : uniqueItems.entrySet()) {
            String code = entry.getKey();
            ImportQuestionTypesFromScoreTemplateRequest.Item source = entry.getValue();
            PteTaskType taskType = parseTaskType(code);
            validateSection(taskType, source.section());

            if (repository.findByCodeAndDeletedFalse(code).isPresent()) {
                continue;
            }

            QuestionTypeDefinition definition = fromTaskType(taskType, nextDisplayOrder++);
            repository.save(definition);
        }

        return uniqueItems.keySet().stream()
                .map(repository::findByCodeAndDeletedFalse)
                .flatMap(Optional::stream)
                .map(QuestionTypeMapper::toResponse)
                .toList();
    }

    private QuestionTypeDefinition fromTaskType(PteTaskType taskType, int displayOrder) {
        QuestionTypeDefinition definition = new QuestionTypeDefinition();
        definition.setCode(taskType.name());
        definition.setDisplayName(toDisplayName(taskType.name()));
        // Score-template rows carry the stable task code, not a short label.
        // The imported row is intentionally editable in the Question Types UI.
        definition.setShortName(taskType.name());
        definition.setSection(taskType.getSection());
        definition.setScored(taskType.isScored());
        definition.setActive(true);
        definition.setDisplayOrder(displayOrder);
        definition.setRequiresAudioPrompt(taskType.requiresAudioPrompt());
        definition.setRequiresImagePrompt(taskType.requiresImagePrompt());
        definition.setRequiresPromptText(taskType.requiresPromptText());
        definition.setRequiresOptions(taskType.requiresOptions());
        definition.setRequiresCorrectAnswer(taskType.requiresCorrectAnswer());
        definition.setRequiresWordCount(taskType.requiresWordCount());
        definition.setRequiresSingleCorrectOption(
                taskType == PteTaskType.MC_READING_SINGLE || taskType == PteTaskType.MC_LISTENING_SINGLE);
        definition.setUsesOptionOrderAsCorrectPosition(taskType == PteTaskType.RE_ORDER_PARAGRAPHS);
        return definition;
    }

    private PteTaskType parseTaskType(String code) {
        try {
            return PteTaskType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new QuestionTypeImportException();
        }
    }

    private void validateSection(PteTaskType taskType, String section) {
        try {
            PteSection importedSection = PteSection.valueOf(section.trim().toUpperCase(Locale.ROOT));
            if (importedSection != taskType.getSection()) {
                throw new QuestionTypeImportException();
            }
        } catch (IllegalArgumentException ex) {
            throw new QuestionTypeImportException();
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String toDisplayName(String code) {
        String[] words = code.split("_");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (displayName.length() > 0) {
                displayName.append(' ');
            }
            displayName.append(word.charAt(0))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return displayName.toString();
    }

    private QuestionTypeDefinition findByPublicId(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(QuestionTypeNotFoundException::new);
    }
}
