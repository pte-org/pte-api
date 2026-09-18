package com.pte.attempt.dto.response;

import java.util.Set;
import java.util.UUID;

/**
 * What {@code reporting} needs to score one attempt (Phase 5): which
 * {@code ScoreTemplate} it was pinned to, and the distinct {@code section}s
 * of its {@code PinnedItem}s — the "tested skills" for this specific attempt,
 * derived rather than stored, since Plan A keeps the manual blueprint +
 * {@code SessionComposition} flow (no declarative {@code selectedSkills} yet;
 * that's Plan B's random-exam-generation concern).
 */
public record AttemptScoreContextView(UUID scoreTemplatePublicId, Set<String> testedSections) {
}
