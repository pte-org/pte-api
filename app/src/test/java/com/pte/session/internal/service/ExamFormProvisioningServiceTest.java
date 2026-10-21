package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.session.domain.ExamForm;
import com.pte.session.domain.ExamGenerationJob;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.FormAssignment;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.internal.repository.ExamFormRepository;
import com.pte.session.internal.repository.ExamGenerationJobRepository;
import com.pte.session.internal.repository.FormAssignmentRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamFormProvisioningServiceTest {

    private static final long SESSION_ID = 7L;
    private static final long BASE_SEED = 42L;

    @Mock
    private ExamFormRepository formRepository;

    @Mock
    private FormAssignmentRepository formAssignmentRepository;

    @Mock
    private ExamGenerationJobRepository jobRepository;

    @Mock
    private AssessmentService assessmentService;

    private ExamFormProvisioningService service;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new ExamFormProvisioningService(formRepository, formAssignmentRepository, jobRepository,
                assessmentService);
    }

    private ExamSession session(FormMode formMode) {
        ExamSession session = new ExamSession();
        session.setId(SESSION_ID);
        session.setName("Mock test");
        session.setTemplatePublicId(UUID.randomUUID());
        session.setFormMode(formMode);
        session.setSelectedSkills(new LinkedHashSet<>(Set.of("SPEAKING")));
        return session;
    }

    private ExamForm form(int index) {
        ExamForm form = new ExamForm();
        form.setFormIndex(index);
        form.setSnapshotPublicId(UUID.randomUUID());
        form.setAlgorithmVersion("v1");
        form.setPoolPolicyFingerprint("template:3");
        return form;
    }

    @Test
    void provision_withoutGeneratedForms_doesNothing() {
        ExamSession session = session(FormMode.SHARED_FORM);
        when(formRepository.findBySessionIdOrderByFormIndexAsc(SESSION_ID)).thenReturn(List.of());

        service.provision(session, List.of(UUID.randomUUID()), caller);

        verify(formAssignmentRepository, never()).save(any());
        verifyNoInteractions(assessmentService);
    }

    @Test
    void provision_sharedForm_pointsNewStudentAtTheSharedForm() {
        ExamSession session = session(FormMode.SHARED_FORM);
        ExamForm shared = form(0);
        UUID studentPublicId = UUID.randomUUID();
        when(formRepository.findBySessionIdOrderByFormIndexAsc(SESSION_ID)).thenReturn(List.of(shared));
        when(formAssignmentRepository.findBySessionIdAndStudentPublicId(SESSION_ID, studentPublicId))
                .thenReturn(Optional.empty());

        service.provision(session, List.of(studentPublicId), caller);

        ArgumentCaptor<FormAssignment> saved = ArgumentCaptor.forClass(FormAssignment.class);
        verify(formAssignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getForm()).isSameAs(shared);
        assertThat(saved.getValue().getStudentPublicId()).isEqualTo(studentPublicId);
        verifyNoInteractions(assessmentService);
    }

    @Test
    void provision_uniqueForm_generatesOneNewFormPerStudentContinuingTheIndex() {
        ExamSession session = session(FormMode.UNIQUE_FORM_PER_STUDENT);
        ExamGenerationJob job = new ExamGenerationJob();
        job.setBaseSeed(BASE_SEED);
        job.setFormsTotal(2);
        job.setFormsCompleted(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID snapshotA = UUID.randomUUID();
        UUID snapshotB = UUID.randomUUID();
        when(formRepository.findBySessionIdOrderByFormIndexAsc(SESSION_ID)).thenReturn(List.of(form(0), form(1)));
        when(jobRepository.findFirstBySessionIdOrderByCreatedAtDesc(SESSION_ID)).thenReturn(Optional.of(job));
        when(formAssignmentRepository.findBySessionIdAndStudentPublicId(eq(SESSION_ID), any()))
                .thenReturn(Optional.empty());
        when(assessmentService.generateDeterministic(eq("Mock test"), eq(session.getTemplatePublicId()),
                eq(ExamFormProvisioningService.formSeed(BASE_SEED, 2)), eq(caller), eq(session.getSelectedSkills())))
                .thenReturn(snapshot(snapshotA));
        when(assessmentService.generateDeterministic(eq("Mock test"), eq(session.getTemplatePublicId()),
                eq(ExamFormProvisioningService.formSeed(BASE_SEED, 3)), eq(caller), eq(session.getSelectedSkills())))
                .thenReturn(snapshot(snapshotB));
        when(formRepository.saveAndFlush(any(ExamForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.provision(session, List.of(first, second), caller);

        ArgumentCaptor<FormAssignment> saved = ArgumentCaptor.forClass(FormAssignment.class);
        verify(formAssignmentRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        List<FormAssignment> assignments = saved.getAllValues();
        assertThat(assignments.get(0).getForm().getFormIndex()).isEqualTo(2);
        assertThat(assignments.get(0).getForm().getSnapshotPublicId()).isEqualTo(snapshotA);
        assertThat(assignments.get(1).getForm().getFormIndex()).isEqualTo(3);
        assertThat(assignments.get(1).getForm().getSnapshotPublicId()).isEqualTo(snapshotB);
        assertThat(assignments.get(1).getForm().getPoolPolicyFingerprint()).isEqualTo("template:3");
        assertThat(job.getFormsTotal()).isEqualTo(4);
        assertThat(job.getFormsCompleted()).isEqualTo(4);
    }

    @Test
    void provision_skipsStudentsThatAlreadyHaveAForm() {
        ExamSession session = session(FormMode.SHARED_FORM);
        UUID studentPublicId = UUID.randomUUID();
        when(formRepository.findBySessionIdOrderByFormIndexAsc(SESSION_ID)).thenReturn(List.of(form(0)));
        when(formAssignmentRepository.findBySessionIdAndStudentPublicId(SESSION_ID, studentPublicId))
                .thenReturn(Optional.of(new FormAssignment()));

        service.provision(session, List.of(studentPublicId), caller);

        verify(formAssignmentRepository, never()).save(any());
    }

    @Test
    void release_removesOnlyThatStudentsAssignment() {
        UUID studentPublicId = UUID.randomUUID();

        service.release(session(FormMode.SHARED_FORM), studentPublicId);

        verify(formAssignmentRepository).deleteBySessionIdAndStudentPublicId(SESSION_ID, studentPublicId);
        verify(formAssignmentRepository, never()).deleteBySessionId(anyLong());
    }

    private SnapshotResponse snapshot(UUID publicId) {
        return new SnapshotResponse(publicId, "Mock test", 1, null, null, 1, null, List.of());
    }
}
