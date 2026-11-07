package com.pte.itembank.internal.generation.job;

import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.dto.GenerationTaskTypeResponse;
import com.pte.itembank.internal.generation.pipeline.ItemGenerator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The task types the form may offer: exactly those a registered generator can make. A type with no generator is not
 * listed and a submit for it is refused, so a job can never be accepted that no one can run.
 */
@Component
public class GenerationTaskTypeCatalog {

    private final List<ItemGenerator> generators;

    public GenerationTaskTypeCatalog(List<ItemGenerator> generators) {
        this.generators = generators;
    }

    public List<GenerationTaskTypeResponse> supported() {
        return Arrays.stream(PteTaskType.values())
                .filter(this::hasGenerator)
                .map(GenerationTaskTypeCatalog::describe)
                .toList();
    }

    /** The supported task type named by {@code raw} (case-insensitive), if any. */
    public Optional<PteTaskType> find(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(PteTaskType.values())
                .filter(type -> type.name().equals(raw.trim().toUpperCase(Locale.ROOT)))
                .filter(this::hasGenerator)
                .findFirst();
    }

    private boolean hasGenerator(PteTaskType type) {
        return generators.stream().anyMatch(generator -> generator.supports(type.name()));
    }

    private static GenerationTaskTypeResponse describe(PteTaskType type) {
        boolean media = type.requiresAudioPrompt() || type.requiresImagePrompt();
        return new GenerationTaskTypeResponse(type.name(), type.getSection().name(), type.requiresAudioPrompt(),
                type.requiresImagePrompt(), media ? QuestionGenerationConstants.ESTIMATED_SECONDS_MEDIA
                : QuestionGenerationConstants.ESTIMATED_SECONDS_TEXT);
    }
}
