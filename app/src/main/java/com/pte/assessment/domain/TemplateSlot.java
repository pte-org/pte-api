package com.pte.assessment.domain;

import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Required number of questions of one task type in a template section. */
@Entity
@Table(name = "template_slots", indexes = {
        @Index(name = "idx_template_slots_section", columnList = "template_section_id")
})
@Getter
@Setter
@NoArgsConstructor
public class TemplateSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_section_id", nullable = false)
    private TemplateSection templateSection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private PteTaskType taskType;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private int orderIndex;
}
