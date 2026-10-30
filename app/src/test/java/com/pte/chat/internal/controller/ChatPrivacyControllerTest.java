package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.consent.ChatConsentService;
import com.pte.chat.internal.service.consent.ChatConsentStatus;
import com.pte.chat.internal.service.privacy.ChatStudentDataService;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Withdrawing consent and deleting one's chats are rights, so they never ask whether practice is unlocked:
 * a student whose subscription lapsed must still be able to take their data back.
 */
class ChatPrivacyControllerTest {

    private static final String CONSENT = ChatConstants.CHAT_PATH + ChatConstants.CONSENT_PATH;
    private static final String HISTORY = ChatConstants.CHAT_PATH + ChatConstants.HISTORY_PATH;
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ChatConsentService consentService = mock(ChatConsentService.class);
    private final ChatStudentDataService dataService = mock(ChatStudentDataService.class);
    private final PracticeEntitlementGate entitlementGate = mock(PracticeEntitlementGate.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(ChatConstants.PROPERTY_ENABLED + "=true").applyTo(context);
        context.register(MethodSecurityConfig.class);
        context.registerBean(ChatConsentController.class, () -> new ChatConsentController(consentService, entitlementGate));
        context.registerBean(ChatHistoryController.class, () -> new ChatHistoryController(dataService));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ChatConsentController.class),
                context.getBean(ChatHistoryController.class)).setControllerAdvice(new GlobalExceptionHandler()).build();
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
    void deletingTheConsentWithdrawsItAndReturnsTheNewStatus() throws Exception {
        when(consentService.withdraw(STUDENT)).thenReturn(new ChatConsentStatus("v-now", false, null));

        mvc.perform(delete(CONSENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consented").value(false))
                .andExpect(jsonPath("$.data.policyVersion").value("v-now"));
        verify(consentService).withdraw(STUDENT);
    }

    @Test
    void deletingTheHistoryDeletesOnlyTheCallersAndReportsTheCount() throws Exception {
        when(dataService.deleteHistory(STUDENT)).thenReturn(4);

        mvc.perform(delete(HISTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedMessages").value(4));
        verify(dataService).deleteHistory(STUDENT);
    }

    @Test
    void neverAsksWhetherPracticeIsUnlocked() throws Exception {
        mvc.perform(delete(CONSENT));
        mvc.perform(delete(HISTORY));

        verifyNoInteractions(entitlementGate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "HOST_ADMIN", "ACADEMIC_STAFF", "ACADEMIC_MANAGER"})
    void aCallerWithoutTheStudentRoleIs403AndNothingIsDeleted(String role) throws Exception {
        authenticate(role);

        mvc.perform(delete(CONSENT)).andExpect(status().isForbidden());
        mvc.perform(delete(HISTORY)).andExpect(status().isForbidden());

        verifyNoInteractions(consentService, dataService);
    }
}
