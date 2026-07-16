package com.aptis.modules.examoperations.domain;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "exam")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "questions")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String hostId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Setter(AccessLevel.NONE) // only addQuestion()/removeQuestion() may change this — invariant (BR-006)
    @Column(nullable = false)
    private Boolean isAssignable = false;

    @Setter(AccessLevel.NONE) // only addQuestion()/removeQuestion() may mutate this collection
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ExamQuestion> questions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Session settings (skill subset, proctoring, availability window, retries) ──

    @Column(name = "skill_subset_reading", nullable = false)
    private Boolean skillSubsetReading = true;

    @Column(name = "skill_subset_listening", nullable = false)
    private Boolean skillSubsetListening = true;

    @Column(name = "skill_subset_writing", nullable = false)
    private Boolean skillSubsetWriting = true;

    @Column(name = "skill_subset_speaking", nullable = false)
    private Boolean skillSubsetSpeaking = true;

    @Column(name = "proctor_required", nullable = false)
    private Boolean proctorRequired = false;

    @Column(name = "availability_start")
    private OffsetDateTime availabilityStart;

    @Column(name = "availability_end")
    private OffsetDateTime availabilityEnd;

    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 0;

    /** Nullable = no proctor assigned. One proctor per exam/room at a time (Phase 7 decision). */
    @Column(name = "proctor_id")
    private Long proctorId;

    public static Exam create(String name, String code, String hostId) {
        Exam exam = new Exam();
        exam.name = name;
        exam.code = code;
        exam.hostId = hostId;
        return exam;
    }

    public void addQuestion(ExamQuestion question) {
        if (question == null) {
            throw new ExamConstraintViolationException("Question cannot be null");
        }
        if (questions.stream().anyMatch(q -> Objects.equals(q.getQuestionId(), question.getQuestionId()))) {
            throw new ExamConstraintViolationException(
                    "Question " + question.getQuestionId() + " already exists in exam " + code);
        }
        question.setExam(this);
        questions.add(question);
        this.isAssignable = true; // BR-006: auto-update when >=1 question
    }

    public void removeQuestion(Long questionId) {
        if (questionId == null) {
            throw new ExamConstraintViolationException("Question ID cannot be null");
        }
        ExamQuestion toRemove = questions.stream()
                .filter(q -> Objects.equals(q.getQuestionId(), questionId))
                .findFirst()
                .orElseThrow(() -> new ExamConstraintViolationException(
                        "Question " + questionId + " not found in exam " + code));

        // BR-009: prevent removal if used in assigned batch
        if (Boolean.TRUE.equals(toRemove.getIsUsedInAssignedBatch())) {
            throw new ExamConstraintViolationException(
                    "Cannot remove question " + questionId + " already used in assigned batch");
        }

        toRemove.setExam(null);
        questions.remove(toRemove);
        if (questions.isEmpty()) {
            this.isAssignable = false; // BR-006: update when no more questions
        }
    }

    public List<ExamQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
