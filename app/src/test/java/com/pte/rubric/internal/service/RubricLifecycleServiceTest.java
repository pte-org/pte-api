package com.pte.rubric.internal.service;

import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricStatus;
import com.pte.rubric.domain.enums.RubricChannel;
import com.pte.rubric.dto.request.PublishRubricRequest;
import com.pte.rubric.dto.request.RejectRubricRequest;
import com.pte.rubric.dto.response.RubricResponse;
import com.pte.rubric.internal.repository.RubricRevisionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RubricLifecycleServiceTest {

    @Mock
    private RubricRevisionRepository repository;

    @Mock
    private AuditLogService auditLogService;

    private RubricLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new RubricLifecycleService(repository, auditLogService);
    }

    @Test
    void publish_retiresPreviousPublishedRevisionAndPinsHash() {
        UUID targetId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        RubricRevision target = revision(targetId, authorId, 2, RubricStatus.PENDING_APPROVAL);
        RubricRevision current = revision(UUID.randomUUID(), UUID.randomUUID(), 1, RubricStatus.PUBLISHED);
        when(repository.findWithDetailsByPublicId(targetId)).thenReturn(Optional.of(target));
        when(repository.findByTaskTypeKeyAndChannelAndStatusForUpdate("WRITE_ESSAY",
                RubricChannel.PRACTICE, RubricStatus.PUBLISHED))
                .thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CurrentUser manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));
        RubricResponse response = service.publish(targetId, new PublishRubricRequest("approved", null), manager);

        assertThat(current.getStatus()).isEqualTo(RubricStatus.RETIRED);
        assertThat(target.getStatus()).isEqualTo(RubricStatus.PUBLISHED);
        assertThat(target.getCanonicalHash()).isNotBlank();
        assertThat(response.status()).isEqualTo("PUBLISHED");
        verify(auditLogService).record(any(), any(), any(), any(), any());
    }

    @Test
    void publish_rejectsManagerSelfApproval() {
        UUID managerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        RubricRevision target = revision(targetId, managerId, 1, RubricStatus.PENDING_APPROVAL);
        when(repository.findWithDetailsByPublicId(targetId)).thenReturn(Optional.of(target));
        CurrentUser manager = new CurrentUser(managerId, null, List.of("ACADEMIC_MANAGER"));

        assertThatThrownBy(() -> service.publish(targetId,
                new PublishRubricRequest("self", null), manager))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void publish_requiresAuditReason() {
        UUID targetId = UUID.randomUUID();
        RubricRevision target = revision(targetId, UUID.randomUUID(), 1, RubricStatus.PENDING_APPROVAL);
        when(repository.findWithDetailsByPublicId(targetId)).thenReturn(Optional.of(target));
        CurrentUser manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));

        assertThatThrownBy(() -> service.publish(targetId, new PublishRubricRequest(" ", null), manager))
                .isInstanceOf(com.pte.rubric.internal.exception.RubricValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void reject_requiresReasonAndReturnsDraft() {
        UUID targetId = UUID.randomUUID();
        RubricRevision target = revision(targetId, UUID.randomUUID(), 1, RubricStatus.PENDING_APPROVAL);
        when(repository.findWithDetailsByPublicId(targetId)).thenReturn(Optional.of(target));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CurrentUser manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));

        RubricResponse response = service.reject(targetId, new RejectRubricRequest("Missing anchors"), manager);

        assertThat(target.getStatus()).isEqualTo(RubricStatus.DRAFT);
        assertThat(target.getRejectionReason()).isEqualTo("Missing anchors");
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    private RubricRevision revision(UUID publicId, UUID authorId, int version, RubricStatus status) {
        RubricRevision revision = new RubricRevision();
        revision.setPublicId(publicId);
        revision.setRubricKey("PTEPREP.PRACTICE.WRITE_ESSAY");
        revision.setTaskTypeKey("WRITE_ESSAY");
        revision.setVersion(version);
        revision.setStatus(status);
        revision.setAuthorUserPublicId(authorId);
        revision.setDocumentJson(validWriteEssayDocument());
        revision.setAnchorsJson("[{\"criterionId\":\"TASK_FULFILMENT\",\"level\":\"DEVELOPING\"}]");
        return revision;
    }

    private String validWriteEssayDocument() {
        String[] criteria = {"TASK_FULFILMENT", "DEVELOPMENT_SUPPORT", "ORGANIZATION_COHERENCE", "GRAMMAR",
                "VOCABULARY", "SPELLING", "FORM"};
        StringBuilder json = new StringBuilder("{\"schemaVersion\":\"1.0\",\"taskTypeKey\":\"WRITE_ESSAY\",\"runtime\":{\"profileKey\":\"PTE.WRITE_ESSAY\",\"profileVersion\":1,\"behaviorKey\":\"TEXT_RESPONSE\",\"rendererKey\":\"WRITE_ESSAY_V1\",\"answerSchemaVersion\":1,\"scoringProfileKey\":\"AI_TEXT\",\"scoringProfileVersion\":1,\"requiredClientCapabilities\":[\"TEXT_INPUT\"],\"screenKey\":\"WRITE_ESSAY_V1\",\"contractVersion\":1,\"scoringMode\":\"SCORED\",\"minSupportedAppVersion\":\"1.0.0\",\"authoringContractKey\":\"PTE.WRITE_ESSAY_AUTHORING\",\"authoringContractVersion\":1},\"criteria\":[");
        for (int i = 0; i < criteria.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"criterionId\":\"").append(criteria[i])
                    .append("\",\"order\":").append(i + 1)
                    .append(",\"required\":true,\"allowedLevels\":[\"NOT_ASSESSABLE\",\"DEVELOPING\"],\"evidencePolicy\":{\"requiredTypes\":[\"PROMPT\",\"RESPONSE_TEXT\",\"FORM_RESULT\"],\"allowNotAssessable\":true,\"minimumCoverage\":\"LIMITED\"},\"levels\":[{\"key\":\"NOT_ASSESSABLE\",\"labelVi\":\"Chua danh gia\",\"labelEn\":\"Not assessable\",\"descriptor\":\"Evidence is unavailable\"},{\"key\":\"DEVELOPING\",\"labelVi\":\"Dang phat trien\",\"labelEn\":\"Developing\",\"descriptor\":\"Approved descriptor\"}]}");
        }
        return json.append("],\"policies\":{\"emptyResponse\":\"return_not_assessable_without_inference\",\"invalidResponse\":\"return_not_assessable_and_explain_input_issue\",\"offTopicResponse\":\"use_task_specific_policy\",\"unsupportedNumericScore\":\"reject\",\"officialScoreClaim\":\"reject\"},\"references\":[{\"referenceKey\":\"ref\",\"sourceArtifact\":\"rubric\",\"sourceRevision\":\"1\",\"contentHash\":\"c8c2764deab99d7a1fc1d6ada5bf2a63361b527e837cc55f99f82ed501a891f1\"}],\"anchors\":[{\"criterionId\":\"TASK_FULFILMENT\",\"level\":\"DEVELOPING\",\"exampleKey\":\"ex\",\"sanitizedResponse\":\"answer\",\"evidenceNote\":\"note\",\"approvalStatus\":\"APPROVED\"}],\"provenance\":{\"sourceDocument\":\"practice-rubric-v1.md\",\"sourceRevision\":\"1\",\"academicApprovalRequired\":true}}").toString();
    }
}
