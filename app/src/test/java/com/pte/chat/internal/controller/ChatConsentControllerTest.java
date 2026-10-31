package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import com.pte.chat.internal.service.consent.ChatConsentService;
import com.pte.chat.internal.service.consent.ChatConsentStatus;
import com.pte.practice.PracticeEntitlementGate;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the consent endpoints: student only, entitlement checked, and the service does the real work. */
class ChatConsentControllerTest {

    private static final String CONSENT = ChatConstants.CHAT_PATH + ChatConstants.CONSENT_PATH;
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-10-29T10:00:00Z");

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ChatConsentService consentService = mock(ChatConsentService.class);
    private final PracticeEntitlementGate entitlementGate = mock(PracticeEntitlementGate.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(ChatConstants.PROPERTY_ENABLED + "=true").applyTo(context);
        context.register(MethodSecurityConfig.class);
        context.registerBean(ChatConsentController.class, () -> new ChatConsentController(consentService, entitlementGate));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ChatConsentController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authenticate("STUDENT");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private static void authenticate(String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(STUDENT.toString())
                .issuedAt(Instant.parse("2026-10-28T00:00:00Z")).expiresAt(Instant.parse("2026-10-30T00:00:00Z"))
                .claim(SecurityClaims.ROLES, List.of(roles))
                .claim(SecurityClaims.TENANT_ID, TENANT.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }

    @Test
    void getReportsTheCurrentVersionAndWhetherTheStudentConsented() throws Exception {
        when(consentService.status(STUDENT)).thenReturn(new ChatConsentStatus("v-now", false, null));

        mvc.perform(get(CONSENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyVersion").value("v-now"))
                .andExpect(jsonPath("$.data.consented").value(false))
                .andExpect(jsonPath("$.data.consentedAt").doesNotExist());
        verify(entitlementGate).assertUnlocked(any(), eq(null));
    }

    @Test
    void postRecordsTheConsentAndReturnsTheNewStatus() throws Exception {
        when(consentService.give(STUDENT, "v-now", true)).thenReturn(new ChatConsentStatus("v-now", true, AT));

        mvc.perform(post(CONSENT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"v-now\",\"ageConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consented").value(true))
                .andExpect(jsonPath("$.data.consentedAt").value("2026-10-29T10:00:00Z"));
    }

    @Test
    void aServiceRejectionBecomesABadRequest() throws Exception {
        when(consentService.give(any(), any(), eq(false))).thenThrow(new ChatInvalidRequestException("age is not confirmed"));

        mvc.perform(post(CONSENT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"v-now\",\"ageConfirmed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ChatConstants.ERR_INVALID_REQUEST));
    }

    @Test
    void aMissingFieldIsABadRequestAndNothingIsStored() throws Exception {
        mvc.perform(post(CONSENT).contentType(MediaType.APPLICATION_JSON).content("{\"policyVersion\":\"v-now\"}"))
                .andExpect(status().isBadRequest());

        verify(consentService, never()).give(any(), any(), eq(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "HOST_ADMIN", "ACADEMIC_STAFF", "ACADEMIC_MANAGER"})
    void aCallerWithoutTheStudentRoleIs403AndNothingReachesTheService(String role) throws Exception {
        authenticate(role);

        mvc.perform(get(CONSENT)).andExpect(status().isForbidden());
        mvc.perform(post(CONSENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyVersion\":\"v-now\",\"ageConfirmed\":true}")).andExpect(status().isForbidden());

        verifyNoInteractions(consentService);
    }
}
