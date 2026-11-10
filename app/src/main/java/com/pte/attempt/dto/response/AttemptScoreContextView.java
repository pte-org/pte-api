package com.pte.attempt.dto.response;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What {@code reporting} needs to score one attempt (Phase 5): which
 * exam definition it was taken from, and the distinct {@code section}s
 * of its exam items — the "tested skills" for this specific attempt,
 * derived rather than stored, since Plan A keeps the manual blueprint +
 * {@code SessionComposition} flow (no declarative {@code selectedSkills} yet;
 * that's Plan B's random-exam-generation concern).
 *
 * <p>{@code expectedItemsByTaskType} counts the exam's items per task type (keys as stored on the exam items),
 * so a question the student left blank can be scored 0. Empty when the caller only knows the tested sections.
 */
public record AttemptScoreContextView(UUID examDefinitionPublicId, Integer examDefinitionVersion,
        Set<String> testedSections, Map<String, Integer> expectedItemsByTaskType) {

    public AttemptScoreContextView {
        expectedItemsByTaskType = expectedItemsByTaskType == null ? Map.of() : Map.copyOf(expectedItemsByTaskType);
    }

    public AttemptScoreContextView(UUID examDefinitionPublicId, Integer examDefinitionVersion,
            Set<String> testedSections) {
        this(examDefinitionPublicId, examDefinitionVersion, testedSections, Map.of());
    }

    /** Source-compatible constructor for existing reporting callers. */
    public AttemptScoreContextView(UUID examDefinitionPublicId, Set<String> testedSections) {
        this(examDefinitionPublicId, null, testedSections, Map.of());
    }
}
