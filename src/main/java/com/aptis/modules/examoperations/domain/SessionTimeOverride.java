package com.aptis.modules.examoperations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.modules.questionbank.domain.Skill;

/**
 * Per-section (skill + part) time override for one exam session. A section with no row
 * here falls back to the skill's default timing (Design Constraint from Phase 4).
 */
@Entity
@Table(
        name = "session_time_override",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "skill", "part"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "exam")
public class SessionTimeOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Skill skill;

    @Column(nullable = false)
    private Integer part;

    @Column(name = "override_minutes", nullable = false)
    private Integer overrideMinutes;

    public static SessionTimeOverride create(Exam exam, Skill skill, Integer part, Integer overrideMinutes) {
        SessionTimeOverride override = new SessionTimeOverride();
        override.exam = exam;
        override.skill = skill;
        override.part = part;
        override.overrideMinutes = overrideMinutes;
        return override;
    }
}
