package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.rubric.service.RubricReadService;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.seedJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The vendor rubric read API with method security on: the four vendor roles read it, every tenant role (Host
 * included) gets 403 (TEST_SCENARIOS_IAM authz-guard pattern), and an unknown task type is a 404.
 */
class RubricReadControllerTest {

    private static final String BASE = RubricConstants.RUBRIC_API_PATH;

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE, seedJson());
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RubricReadService service = new RubricReadService(versions, CATALOG,
                new RubricResolver(versions, CATALOG, CODEC,
                org.mockito.Mockito.mock(com.pte.assessment.ExamDefinitionService.class)));
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfig.class);
        context.registerBean(RubricReadController.class, () -> new RubricReadController(service));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(RubricReadController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(versions.findByPublicId(active.getPublicId())).thenReturn(Optional.of(active));
        when(versions.findAllByOrderByVersionNoDesc()).thenReturn(List.of(active));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void vendorRolesReadAll22TaskTypes(String role) throws Exception {
        authenticate(role);

        mvc.perform(get(BASE + "/task-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(22))
                .andExpect(jsonPath("$.data[0].versionNo").value(active.getVersionNo()));
    }

    @Test
    void oneTaskTypeHasItsTraitsGateAndUncalibratedLabel() throws Exception {
        authenticate("ACADEMIC_STAFF");

        mvc.perform(get(BASE + "/task-types/DESCRIBE_IMAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("DESCRIBE_IMAGE"))
                .andExpect(jsonPath("$.data.traits.length()").value(3))
                .andExpect(jsonPath("$.data.gates[0].traitKey").value("CONTENT"))
                .andExpect(jsonPath("$.data.azureMapping.calibrated").value(false))
                .andExpect(jsonPath("$.data.azureMapping.label")
                        .value(RubricConstants.AZURE_MAPPING_UNCALIBRATED_LABEL))
                .andExpect(jsonPath("$.data.azureMapping.version").value("1"))
                .andExpect(jsonPath("$.data.azureMapping.pronunciationBands.length()").value(5))
                .andExpect(jsonPath("$.data.azureMapping.oralFluencyBands[4].band").value(5))
                .andExpect(jsonPath("$.data.azureMapping.oralFluencyInput")
                        .value(RubricConstants.AZURE_ORAL_FLUENCY_INPUT_VI));
    }

    @Test
    void aRequestedVersionIdIsHonoured() throws Exception {
        authenticate("PLATFORM_ADMIN");

        mvc.perform(get(BASE + "/task-types/WRITE_ESSAY").param("versionId", active.getPublicId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionPublicId").value(active.getPublicId().toString()))
                .andExpect(jsonPath("$.data.formBands.length()").value(3));
    }

    @Test
    void anUnknownTaskTypeIs404() throws Exception {
        authenticate("PLATFORM_ADMIN");

        mvc.perform(get(BASE + "/task-types/NOT_A_TASK"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN));
    }

    @Test
    void anUnknownVersionIs404() throws Exception {
        authenticate("PLATFORM_ADMIN");
        UUID unknown = UUID.randomUUID();
        when(versions.findByPublicId(unknown)).thenReturn(Optional.empty());

        mvc.perform(get(BASE + "/versions/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(RubricConstants.RUBRIC_VERSION_NOT_FOUND));
    }

    @Test
    void versionsAreListed() throws Exception {
        authenticate("ACADEMIC_MANAGER");

        mvc.perform(get(BASE + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST_ADMIN", "EXAMINER", "PROCTOR", "STUDENT", "PLATFORM_MANAGER"})
    void everyOtherRoleIs403OnEveryEndpoint(String role) throws Exception {
        authenticate(role);

        mvc.perform(get(BASE + "/task-types")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/task-types/DESCRIBE_IMAGE")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/versions")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/versions/" + active.getPublicId())).andExpect(status().isForbidden());

        verifyNoInteractions(versions);
    }

    private static void authenticate(String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.parse("2026-11-04T00:00:00Z")).expiresAt(Instant.parse("2026-11-05T00:00:00Z"))
                .claim(SecurityClaims.ROLES, List.of(roles)).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }
}
