package com.pte.assessment.domain;

import com.pte.assessment.domain.enums.TemplateStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Platform-owned reusable exam composition selected by a tenant at exam creation. */
@Entity
@Table(name = "exam_templates", indexes = {
        @Index(name = "idx_exam_templates_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamTemplate extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String description;

    /** Kept for aggregate consistency with {@link ExamBlueprint}; always null for this resource. */
    @Column
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TemplateStatus status = TemplateStatus.DRAFT;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("orderIndex ASC")
    private List<TemplateSection> sections = new ArrayList<>();

    public void addSection(TemplateSection section) {
        section.setTemplate(this);
        sections.add(section);
    }

    public void archive() {
        this.status = TemplateStatus.ARCHIVED;
    }
}
