package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.internal.rubric.service.RubricVersionCommandService;
import com.pte.scoring.internal.rubric.workflow.RubricDiffService;
import com.pte.scoring.internal.rubric.workflow.RubricAuditQuery;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RubricVersionControllerTest {
    @Configuration @EnableMethodSecurity static class SecurityConfig { }
    private final RubricVersionCommandService commands = mock(RubricVersionCommandService.class);
    private final RubricDiffService diffs = mock(RubricDiffService.class);
    private final RubricAuditQuery audits = mock(RubricAuditQuery.class);
    private final String id = UUID.randomUUID().toString();
    private static final String BASE = "/api/v1/scoring-rubrics/versions";
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfig.class);
        context.registerBean(RubricVersionController.class, () -> new RubricVersionController(commands, diffs, audits));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(RubricVersionController.class)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    @ParameterizedTest @ValueSource(strings = {"ACADEMIC_STAFF", "PLATFORM_AUTHOR", "ACADEMIC_MANAGER", "PLATFORM_ADMIN"})
    void vendorRolesCreateEditSubmitAndReadDiff(String role) throws Exception {
        authenticate(role);
        mvc.perform(post(BASE).contentType("application/json").content("{\"parameters\":{}}" )).andExpect(status().isOk());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"parameters\":{},\"expectedLockVersion\":0}" )).andExpect(status().isOk());
        mvc.perform(patch(BASE + "/" + id + "/status").contentType("application/json").content("{\"action\":\"SUBMIT\"}" )).andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id + "/diff")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + id + "/activations")).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void authorsCannotActivateOrRejectEvenWithAConfirmation(String role) throws Exception {
        authenticate(role);
        for (String action : List.of("ACTIVATE", "REJECT")) mvc.perform(patch(BASE + "/" + id + "/status").contentType("application/json")
                .content("{\"action\":\"" + action + "\",\"reason\":\"Synthetic reason\",\"confirmedDiffDigest\":\"abc\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(commands);
    }
    @ParameterizedTest @ValueSource(strings = {"ACADEMIC_MANAGER", "PLATFORM_ADMIN"})
    void reviewersCanApproveAndReject(String role) throws Exception {
        authenticate(role);
        for (String action : List.of("ACTIVATE", "REJECT")) mvc.perform(patch(BASE + "/" + id + "/status").contentType("application/json")
                .content("{\"action\":\"" + action + "\",\"reason\":\"Synthetic reason\",\"expectedActiveVersionPublicId\":\"" + id
                        + "\",\"confirmedDiffDigest\":\"" + "a".repeat(64) + "\"}"))
                .andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"HOST_ADMIN", "EXAMINER", "STUDENT", "PROCTOR", "PLATFORM_MANAGER"})
    void nonVendorRolesCannotWriteReadDiffOrAudit(String role) throws Exception {
        authenticate(role);
        mvc.perform(post(BASE).contentType("application/json").content("{\"parameters\":{}}" )).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/" + id).contentType("application/json").content("{\"parameters\":{}}" )).andExpect(status().isForbidden());
        mvc.perform(patch(BASE + "/" + id + "/status").contentType("application/json").content("{\"action\":\"SUBMIT\"}" )).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + id + "/diff")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + id + "/activations")).andExpect(status().isForbidden());
        verifyNoInteractions(commands, diffs, audits);
    }
    private void authenticate(String role) {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(UUID.randomUUID().toString()).claim(SecurityClaims.ROLES, List.of(role)).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }
}
