package com.pte.practice.internal.domain;

import com.pte.practice.internal.domain.enums.PracticeMockTestStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A fixed full mock test: its questions are pinned in {@link PracticeMockTestItem} when it is generated, so every
 * student sees the same test and it does not change when the question bank does.
 */
@Entity
@Table(name = "practice_mock_tests")
@Getter
@Setter
@NoArgsConstructor
public class PracticeMockTest extends BaseEntity {

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false)
    private long seed;

    /** The Score Template the test was built from; its weights score the test later. */
    @Column(name = "template_public_id")
    private UUID templatePublicId;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeMockTestStatus status = PracticeMockTestStatus.PUBLISHED;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Version
    private Long version;
}
