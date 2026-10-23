package com.pte.rubric.internal.service;

import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricStatus;
import com.pte.rubric.dto.request.CreateRubricRequest;
import com.pte.rubric.dto.response.RubricResponse;
import com.pte.rubric.internal.repository.RubricRevisionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RubricDraftServiceTest {

    @Mock
    private RubricRevisionRepository repository;

    @Mock
    private AuditLogService auditLogService;

    @Test
    void createDraft_assignsNextVersionAndAuthenticatedAuthor() {
        RubricDraftService service = new RubricDraftService(repository, auditLogService);
        when(repository.findAllByRubricKeyForUpdate("PTEPREP.PRACTICE.WRITE_ESSAY")).thenReturn(List.of());
        when(repository.findMaxVersionByRubricKey("PTEPREP.PRACTICE.WRITE_ESSAY")).thenReturn(3);
        when(repository.save(any())).thenAnswer(invocation -> {
            RubricRevision saved = invocation.getArgument(0);
            saved.setPublicId(java.util.UUID.randomUUID());
            return saved;
        });

        CurrentUser staff = new CurrentUser(java.util.UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
        RubricResponse response = service.createDraft(
                new CreateRubricRequest("PTEPREP.PRACTICE.WRITE_ESSAY", "WRITE_ESSAY", "Essay rubric",
                        "{\"criteria\":[{\"criterionId\":\"GRAMMAR\",\"levels\":[\"DEVELOPING\"]}]}", "initial"),
                staff);

        assertThat(response.version()).isEqualTo(4);
        assertThat(response.status()).isEqualTo(RubricStatus.DRAFT.name());
        assertThat(response.authorUserPublicId()).isEqualTo(staff.userId());
    }
}
