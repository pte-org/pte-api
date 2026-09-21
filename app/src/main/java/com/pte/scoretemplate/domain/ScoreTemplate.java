package com.pte.scoretemplate.domain;

import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A versioned PTE scoring scheme (e.g. "PTE_Score_Template"): which task types exist,
 * how many questions of each, their timing, how they are scored, and their
 * weight toward each skill/Overall. {@code ExamSnapshot} pins one by
 * {@code publicId} at publish time (assessment module, Phase 2) so a
 * published exam's scoring never drifts when a later template is activated.
 *
 * <p>Lifecycle: DRAFT (freely editable) -> ACTIVE (exactly one at a time,
 * see {@code ScoreTemplateAdminService#activate}) -> RETIRED. ACTIVE/RETIRED
 * are immutable once reached — enforced in the service layer, mirroring
 * {@code ExamSnapshot}'s own write-once convention, not a DB trigger.
 */
@Entity
@Table(name = "score_templates", indexes = {
        @Index(name = "idx_score_templates_code", columnList = "code")
})
@Getter
@Setter
@NoArgsConstructor
public class ScoreTemplate extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoreTemplateStatus status = ScoreTemplateStatus.DRAFT;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<ScoreTemplateItem> items = new ArrayList<>();

    public void addItem(ScoreTemplateItem item) {
        item.setTemplate(this);
        items.add(item);
    }
}
