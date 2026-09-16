package com.pte.assessment.domain;

import com.pte.itembank.domain.enums.PteSection;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/** One weighted PTE section in an {@link ExamTemplate}. */
@Entity
@Table(name = "template_sections", indexes = {
        @Index(name = "idx_template_sections_template", columnList = "template_id")
})
@Getter
@Setter
@NoArgsConstructor
public class TemplateSection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ExamTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PteSection section;

    @Column(nullable = false)
    private int weightPercent;

    @Column(nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "templateSection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("orderIndex ASC")
    private List<TemplateSlot> slots = new ArrayList<>();

    public void addSlot(TemplateSlot slot) {
        slot.setTemplateSection(this);
        slots.add(slot);
    }
}
