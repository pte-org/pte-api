package com.aptis.modules.examdelivery.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.aptis.modules.examoperations.domain.Exam;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;

@Entity
@Table(name = "exam_attempt")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "answers")
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false, name = "exam_id")
    private Long examId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", insertable = false, updatable = false)
    private Exam exam;

    @Setter(AccessLevel.NONE) // only submit() may change this — invariant (BR-003)
    @Column(nullable = false)
    private Boolean isSubmitted = false;

    @Setter(AccessLevel.NONE) // only submit() may set this
    private LocalDateTime submittedAt;

    @Setter(AccessLevel.NONE) // collection mutated only via internal logic, not direct setter
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AttemptAnswer> answers = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void submit() {
        if (this.isSubmitted) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    ExamDeliveryConstants.ATTEMPT_ALREADY_SUBMITTED_DOMAIN);
        }
        this.isSubmitted = true;
        this.submittedAt = LocalDateTime.now();
    }

    public List<AttemptAnswer> getAnswers() {
        return Collections.unmodifiableList(answers);
    }
}
