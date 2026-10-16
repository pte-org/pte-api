package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.session.domain.ExamForm;
import com.pte.session.domain.ExamGenerationJob;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.FormAssignment;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.internal.exception.GenerationNotReadyException;
import com.pte.session.internal.repository.ExamFormRepository;
import com.pte.session.internal.repository.ExamGenerationJobRepository;
import com.pte.session.internal.repository.FormAssignmentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Keeps an exam's generated forms in step with its roster after publishing. Entitlement looks up the
 * student's {@link FormAssignment} for unique-form exams, so a student added later is not entitled until
 * they have a form of their own (or the shared one).
 */
@Service
class ExamFormProvisioningService {

    private final ExamFormRepository formRepository;
    private final FormAssignmentRepository formAssignmentRepository;
    private final ExamGenerationJobRepository jobRepository;
    private final AssessmentService assessmentService;

    ExamFormProvisioningService(ExamFormRepository formRepository, FormAssignmentRepository formAssignmentRepository,
                                ExamGenerationJobRepository jobRepository, AssessmentService assessmentService) {
        this.formRepository = formRepository;
        this.formAssignmentRepository = formAssignmentRepository;
        this.jobRepository = jobRepository;
        this.assessmentService = assessmentService;
    }

    /** Seed of the {@code index}-th form of a job, so generation and late additions draw from the same sequence. */
    static long formSeed(long baseSeed, int index) {
        return baseSeed ^ (0x9E3779B97F4A7C15L * (index + 1L));
    }

    /**
     * Assigns a form to each newly enrolled student. Exams without generated forms (created before the
     * workflow existed) keep the single snapshot on the session and need nothing.
     */
    @Transactional
    void provision(ExamSession session, List<UUID> studentPublicIds, CurrentUser caller) {
        if (studentPublicIds.isEmpty()) {
            return;
        }
        List<ExamForm> forms = formRepository.findBySessionIdOrderByFormIndexAsc(session.getId());
        if (forms.isEmpty()) {
            return;
        }
        ExamGenerationJob job = session.getFormMode() == FormMode.UNIQUE_FORM_PER_STUDENT
                ? jobRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId())
                        .orElseThrow(GenerationNotReadyException::new)
                : null;
        ExamForm last = forms.get(forms.size() - 1);
        int nextIndex = last.getFormIndex() + 1;
        for (UUID studentPublicId : studentPublicIds) {
            if (formAssignmentRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId).isPresent()) {
                continue;
            }
            ExamForm form = forms.get(0);
            if (job != null) {
                form = newForm(session, last, job, nextIndex++, caller);
                job.setFormsTotal(job.getFormsTotal() + 1);
                job.setFormsCompleted(job.getFormsCompleted() + 1);
            }
            FormAssignment assignment = new FormAssignment();
            assignment.setSession(session);
            assignment.setForm(form);
            assignment.setStudentPublicId(studentPublicId);
            formAssignmentRepository.save(assignment);
        }
        if (job != null) {
            jobRepository.save(job);
        }
    }

    /** Drops the student's assignment; the form itself stays, since snapshots are immutable and shared by reports. */
    @Transactional
    void release(ExamSession session, UUID studentPublicId) {
        formAssignmentRepository.deleteBySessionIdAndStudentPublicId(session.getId(), studentPublicId);
    }

    private ExamForm newForm(ExamSession session, ExamForm template, ExamGenerationJob job, int index,
                             CurrentUser caller) {
        long seed = formSeed(job.getBaseSeed(), index);
        SnapshotResponse snapshot = assessmentService.generateDeterministic(
                session.getName(), session.getTemplatePublicId(), seed, caller, session.getSelectedSkills());
        ExamForm form = new ExamForm();
        form.setSession(session);
        form.setSnapshotPublicId(snapshot.publicId());
        form.setFormIndex(index);
        form.setFormSeed(seed);
        form.setAlgorithmVersion(template.getAlgorithmVersion());
        form.setPoolPolicyFingerprint(template.getPoolPolicyFingerprint());
        return formRepository.saveAndFlush(form);
    }
}
