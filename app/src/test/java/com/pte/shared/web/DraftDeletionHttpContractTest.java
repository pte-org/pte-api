package com.pte.shared.web;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.controller.PlanController;
import com.pte.billing.internal.exception.PlanLifecycleException;
import com.pte.billing.internal.service.PlanService;
import com.pte.itembank.ItembankService;
import com.pte.itembank.internal.controller.QuestionController;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.service.QuestionDeletionService;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP mapping/body tests only: standalone MVC does not exercise JWT filters or method security. */
class DraftDeletionHttpContractTest {
    private final PlanService plans = mock(PlanService.class);
    private final QuestionDeletionService questions = mock(QuestionDeletionService.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private final UUID id = UUID.randomUUID();
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        context = mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(caller);
        mvc = MockMvcBuilders.standaloneSetup(new PlanController(plans),
                        new QuestionController(mock(ItembankService.class), questions))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void tearDown() { context.close(); }

    @Test void planDeleteReturnsEmpty204AndPassesAuthenticatedActor() throws Exception {
        mvc.perform(delete("/api/v1/plans/{publicId}", id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(plans).deleteDraft(id, caller);
    }

    @Test void questionDeleteReturnsEmpty204AndPassesAuthenticatedActor() throws Exception {
        mvc.perform(delete("/api/v1/questions/{publicId}", id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(questions).deleteDraft(id, caller);
    }

    @Test void referencedDraftReturns409AndMachineReadableCode() throws Exception {
        doThrow(new PlanLifecycleException(BillingConstants.PLAN_HAS_REFERENCES,
                BillingConstants.PLAN_HAS_REFERENCES_MESSAGE)).when(plans).deleteDraft(id, caller);
        mvc.perform(delete("/api/v1/plans/{publicId}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(BillingConstants.PLAN_HAS_REFERENCES));
    }

    @Test void wrongScopeReturns404() throws Exception {
        doThrow(new QuestionNotFoundException()).when(questions).deleteDraft(id, caller);
        mvc.perform(delete("/api/v1/questions/{publicId}", id)).andExpect(status().isNotFound());
    }

    @Test void serviceAuthorizationDenialReturns403() throws Exception {
        doThrow(new AccessDeniedException("Fixture denial")).when(plans).deleteDraft(id, caller);
        mvc.perform(delete("/api/v1/plans/{publicId}", id)).andExpect(status().isForbidden());
    }

    @Test void staleWriterConflictReturns409InsteadOf500() throws Exception {
        doThrow(new OptimisticLockingFailureException("Fixture stale version")).when(questions).deleteDraft(id, caller);
        mvc.perform(delete("/api/v1/questions/{publicId}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));
    }
}
