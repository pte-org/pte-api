package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.internal.rubric.service.RubricSimulationService;
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
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RubricSimulationControllerTest {
    @Configuration @EnableMethodSecurity static class SecurityConfig { }
    private final RubricSimulationService service = mock(RubricSimulationService.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private static final String PATH = "/api/v1/scoring-rubrics/simulations";
    private static final String BODY = "{\"taskType\":\"DESCRIBE_IMAGE\",\"traitScores\":{\"CONTENT\":3.5}}";

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfig.class);
        context.registerBean(RubricSimulationController.class, () -> new RubricSimulationController(service));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(RubricSimulationController.class))
                .setControllerAdvice(new GlobalExceptionHandler(), new RubricSimulationBodyAdvice()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void vendorRolesCanSimulate(String role) throws Exception {
        authenticate(role);
        mvc.perform(post(PATH).contentType("application/json").content(BODY)).andExpect(status().isOk());
        verify(service).simulate(argThat(request -> request.traitScores().get("CONTENT").toPlainString().equals("3.5")));
    }
    @ParameterizedTest @ValueSource(strings = {"HOST_ADMIN", "EXAMINER", "STUDENT", "PROCTOR", "PLATFORM_MANAGER"})
    void tenantAndOtherRolesAreForbidden(String role) throws Exception {
        authenticate(role);
        mvc.perform(post(PATH).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void missingScoresAre400() throws Exception {
        authenticate("ACADEMIC_STAFF");
        mvc.perform(post(PATH).contentType("application/json").content("{\"taskType\":\"WRITE_ESSAY\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rawBodyLimitIncludesWhitespaceAndDoesNotRequireContentLength() throws Exception {
        authenticate("ACADEMIC_STAFF");
        mvc.perform(post(PATH).contentType("application/json").content(BODY + " ".repeat(65536)))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }
    private void authenticate(String role) {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .claim(SecurityClaims.ROLES, List.of(role)).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }
}
