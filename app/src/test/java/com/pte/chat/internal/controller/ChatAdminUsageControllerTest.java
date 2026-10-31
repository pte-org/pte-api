package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.report.ChatUsageDay;
import com.pte.chat.internal.service.report.ChatUsageReportService;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only platform admins read the usage report, and it takes plain ISO dates. */
class ChatAdminUsageControllerTest {

    private static final String USAGE = ChatConstants.ADMIN_USAGE_PATH;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 28);
    private static final LocalDate TO = LocalDate.of(2026, 10, 29);

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ChatUsageReportService reportService = mock(ChatUsageReportService.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(ChatConstants.PROPERTY_ENABLED + "=true").applyTo(context);
        context.register(MethodSecurityConfig.class);
        context.registerBean(ChatAdminUsageController.class, () -> new ChatAdminUsageController(reportService));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ChatAdminUsageController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate("PLATFORM_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private static void authenticate(String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.parse("2026-10-28T00:00:00Z")).expiresAt(Instant.parse("2026-10-30T00:00:00Z"))
                .claim(SecurityClaims.ROLES, List.of(roles)).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }

    @Test
    void returnsTheDaysForTheRequestedRange() throws Exception {
        when(reportService.report(FROM, TO)).thenReturn(List.of(
                new ChatUsageDay(FROM, 6, 5, 1, 0, 1000, 2000, 0, new BigDecimal("0.0012"))));

        mvc.perform(get(USAGE).param("from", "2026-10-28").param("to", "2026-10-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].date").value("2026-10-28"))
                .andExpect(jsonPath("$.data[0].messages").value(6))
                .andExpect(jsonPath("$.data[0].ok").value(5))
                .andExpect(jsonPath("$.data[0].cacheHits").doesNotExist())
                .andExpect(jsonPath("$.data[0].tokensIn").value(1000))
                .andExpect(jsonPath("$.data[0].estimatedCostUsd").value(0.0012));
        verify(reportService).report(FROM, TO);
    }

    @Test
    void aMissingOrMalformedDateIsABadRequestAndNothingIsRun() throws Exception {
        mvc.perform(get(USAGE).param("from", "2026-10-28")).andExpect(status().isBadRequest());
        mvc.perform(get(USAGE).param("from", "yesterday").param("to", "2026-10-29")).andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "HOST_ADMIN", "ACADEMIC_STAFF", "ACADEMIC_MANAGER"})
    void anyoneButAPlatformAdminIs403(String role) throws Exception {
        authenticate(role);

        mvc.perform(get(USAGE).param("from", "2026-10-28").param("to", "2026-10-29")).andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }
}
