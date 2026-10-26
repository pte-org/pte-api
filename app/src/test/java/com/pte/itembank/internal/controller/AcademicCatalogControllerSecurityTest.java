package com.pte.itembank.internal.controller;

import com.pte.itembank.dto.request.TaskTypeStatusAction;
import com.pte.itembank.dto.request.TaskTypeStatusRequest;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeRolloutProperties;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AcademicCatalogControllerSecurityTest {

    @Test
    void legacyAndCanonicalCatalogsExposeTheAcademicAuthoringRoles() {
        String expected = "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')";

        assertThat(QuestionTypeController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
        assertThat(TaskTypeController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
    }

    @Test
    void approvalIsManagerOrAdminOnlyWhileSubmissionStaysOpenToAuthors() {
        CurrentUser author = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
        CurrentUser manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));
        QuestionTypeService service = mock(QuestionTypeService.class);
        TaskTypeController controller = new TaskTypeController(service, new TaskTypeRolloutProperties());
        UUID id = UUID.randomUUID();
        try (MockedStatic<CurrentUserContext> context = mockStatic(CurrentUserContext.class)) {
            context.when(CurrentUserContext::required).thenReturn(author);
            controller.changeStatus(id, new TaskTypeStatusRequest(TaskTypeStatusAction.SUBMIT_APPROVAL, null));
            verify(service).submitTaskType(id, author);
            assertThatThrownBy(() -> controller.changeStatus(id,
                    new TaskTypeStatusRequest(TaskTypeStatusAction.APPROVE, null)))
                    .isInstanceOf(AccessDeniedException.class);

            context.when(CurrentUserContext::required).thenReturn(manager);
            controller.changeStatus(id, new TaskTypeStatusRequest(TaskTypeStatusAction.APPROVE, null));
            verify(service).approveTaskType(id, manager);
        }
    }
}
