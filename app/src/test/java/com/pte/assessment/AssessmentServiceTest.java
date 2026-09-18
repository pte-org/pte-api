package com.pte.assessment;

import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.ExamGenerationService;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private SnapshotPublishService snapshotPublishService;
    @Mock
    private ExamGenerationService examGenerationService;

    private AssessmentService service;

    @BeforeEach
    void setUp() {
        service = new AssessmentService(snapshotPublishService, examGenerationService);
    }

    @Test
    void generateAndPublish_delegatesToExamGenerationService_returnsItsResult() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), java.util.List.of("HOST_ADMIN"));
        Set<String> skills = Set.of("SPEAKING", "WRITING");
        SnapshotResponse expected = new SnapshotResponse(
                UUID.randomUUID(), "Exam", 1, UUID.randomUUID(), UUID.randomUUID(), 1, caller.tenantId(), java.util.List.of());
        when(examGenerationService.generate("Exam", skills, caller)).thenReturn(expected);

        SnapshotResponse actual = service.generateAndPublish("Exam", skills, caller);

        assertThat(actual).isSameAs(expected);
    }
}
