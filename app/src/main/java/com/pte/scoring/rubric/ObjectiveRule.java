package com.pte.scoring.rubric;

/**
 * How an objective task type is scored, for display only: the executable rule stays in the objective scorers. The
 * code names the rule family (for example PLUS_MINUS_FLOOR_ZERO: +1 per correct choice, -1 per wrong one, never
 * below 0).
 */
public record ObjectiveRule(String code, String descriptionVi) {
}
