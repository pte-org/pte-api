package com.pte.assessment.domain;

import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "exam_definitions", indexes = {
        @Index(name = "idx_exam_definitions_code", columnList = "code")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamDefinition extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private String name;

    /** Platform academic author; null is retained for legacy templates. */
    @Column(name = "author_user_public_id")
    private java.util.UUID authorUserPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamDefinitionStatus status = ExamDefinitionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_policy", nullable = false, length = 16)
    private TemplatePolicy templatePolicy = TemplatePolicy.STANDARD_PTE;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** Guards draft edits: two overlapping item replacements fail instead of duplicating rows. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exam_definition_items", joinColumns = @JoinColumn(name = "exam_definition_id"))
    @OrderBy("sequence ASC")
    private List<ExamDefinitionItem> items = new ArrayList<>();

    public void addItem(ExamDefinitionItem item) {
        items.add(item);
    }
}
