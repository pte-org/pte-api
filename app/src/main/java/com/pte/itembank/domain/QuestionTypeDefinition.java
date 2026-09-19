package com.pte.itembank.domain;

import com.pte.itembank.domain.enums.PteSection;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted catalog metadata for one PTE question type.
 *
 * <p>The task code remains the stable integration key used by exam delivery,
 * scoring and existing question rows. Human-facing labels, availability,
 * ordering and authoring requirements live here so clients do not maintain a
 * second task-type vocabulary in source code.
 */
@Entity
@Table(name = "question_types", indexes = {
        @Index(name = "idx_question_types_active_order", columnList = "active, display_order")
})
@Getter
@Setter
@NoArgsConstructor
public class QuestionTypeDefinition extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "short_name", nullable = false, length = 32)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PteSection section;

    @Column(nullable = false)
    private boolean scored;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "requires_audio_prompt", nullable = false)
    private boolean requiresAudioPrompt;

    @Column(name = "requires_image_prompt", nullable = false)
    private boolean requiresImagePrompt;

    @Column(name = "requires_prompt_text", nullable = false)
    private boolean requiresPromptText;

    @Column(name = "requires_options", nullable = false)
    private boolean requiresOptions;

    @Column(name = "requires_correct_answer", nullable = false)
    private boolean requiresCorrectAnswer;

    @Column(name = "requires_word_count", nullable = false)
    private boolean requiresWordCount;

    /** True for MC single-answer task types. */
    @Column(name = "requires_single_correct_option", nullable = false)
    private boolean requiresSingleCorrectOption;

    /** True when option order represents the authored correct position. */
    @Column(name = "uses_option_order_as_correct_position", nullable = false)
    private boolean usesOptionOrderAsCorrectPosition;
}
