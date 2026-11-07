package com.pte.itembank;

import java.util.List;
import java.util.Optional;

/**
 * SPI for providing rubric trait information for question generation and review.
 * Decouples itembank.generation from scoring.rubric to avoid module cycles.
 *
 * Implementers provide trait metadata (names, scale, levels) without exposing
 * the full RubricCatalog or scoring domain logic. This allows generation context
 * to be independent and portable.
 */
public interface GenerationRubricTraits {

    /**
     * Finds rubric metadata for a task type key.
     *
     * @param taskTypeKey the task type identifier (e.g. "WRITE_ESSAY")
     * @return task rubric metadata if available
     */
    Optional<TaskTraitInfo> find(String taskTypeKey);

    /**
     * Metadata about a single trait within a task's rubric.
     *
     * @param traitKey stable identifier for the trait (e.g. "grammar", "vocabulary")
     * @param displayNameEn user-facing display name in English
     * @param maxScore maximum score for this trait (e.g. 5 for per-word traits, 3 for bands)
     * @param levels list of level descriptors, indexed 0..maxScore
     */
    record TraitInfo(
            String traitKey,
            String displayNameEn,
            Integer maxScore,
            List<LevelDescriptor> levels
    ) {
    }

    /**
     * Descriptor for a single score level within a trait.
     *
     * @param level score value (0..maxScore)
     * @param text human-readable description of what this level means
     */
    record LevelDescriptor(int level, String text) {
    }

    /**
     * Metadata about all traits for a task type.
     *
     * @param taskTypeKey the task type identifier
     * @param traits list of all traits for this task, in order
     */
    record TaskTraitInfo(String taskTypeKey, List<TraitInfo> traits) {
    }
}
