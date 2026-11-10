package com.pte.itembank.internal.generation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One requested question of a job. Rows are created with the job; the worker changes them by bulk update. */
@Entity
@Table(name = "question_generation_job_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_qgen_job_items_job_index", columnNames = {"job_id", "item_index"}))
@Getter
@Setter
@NoArgsConstructor
public class QuestionGenerationJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "item_index", nullable = false, updatable = false)
    private int itemIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ItemStage stage = ItemStage.QUEUED;

    @Column(name = "question_public_id")
    private UUID questionPublicId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_note", length = 300)
    private String failureNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
