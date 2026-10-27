package com.pte.practice.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One pinned question of a mock test, in the order the student answers it. */
@Entity
@Table(name = "practice_mock_test_items")
@Getter
@Setter
@NoArgsConstructor
public class PracticeMockTestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mock_test_id", nullable = false)
    private Long mockTestId;

    @Column(name = "question_public_id", nullable = false)
    private UUID questionPublicId;

    @Column(name = "task_code", nullable = false, length = 64)
    private String taskCode;

    @Column(nullable = false, length = 32)
    private String section;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
