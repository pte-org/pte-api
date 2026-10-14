package com.pte.itembank.domain.enums;

/**
 * Which product a question serves. Fixed at creation — a question never moves
 * between pools, and only {@link #EXAM} questions can enter exam generation.
 */
public enum QuestionPool {
    EXAM,
    PRACTICE
}
