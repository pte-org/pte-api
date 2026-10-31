package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.exception.ChatConsentRequiredException;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.service.ChatLatestSession;
import com.pte.chat.internal.service.ChatReply;
import com.pte.chat.internal.service.ChatService;
import com.pte.chat.internal.service.consent.ChatConsentService;
import com.pte.chat.internal.service.consent.ChatConsentStatus;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * What the web client reads is what the backend writes: every body below is produced by the real controllers,
 * the real serialisation and the real error handler, and compared with {@code chat/api-contract-v1.json}, the
 * same file pte-practice's contract test reads. A renamed or retyped field fails here, not in a student's browser.
 */
class ChatApiContractTest {

    private static final String BASE = ChatConstants.CHAT_PATH;
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SESSION = UUID.fromString("0b8f2c3e-6d4a-4f7e-9c1b-2a3d4e5f6a7b");
    private static final Instant RESET_AT = Instant.parse("2026-10-29T17:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ChatService chatService = mock(ChatService.class);
    private final ChatConsentService consentService = mock(ChatConsentService.class);
    private final PracticeEntitlementGate entitlementGate = mock(PracticeEntitlementGate.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private JsonNode contract;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = new ClassPathResource("chat/api-contract-v1.json").getInputStream()) {
            contract = JSON.readTree(in);
        }
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(ChatConstants.PROPERTY_ENABLED + "=true").applyTo(context);
        context.register(MethodSecurityConfig.class);
        context.registerBean(ChatController.class, () -> new ChatController(chatService));
        context.registerBean(ChatConsentController.class, () -> new ChatConsentController(consentService, entitlementGate));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ChatController.class), context.getBean(ChatConsentController.class))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(STUDENT.toString())
                .issuedAt(Instant.parse("2026-10-28T00:00:00Z")).expiresAt(Instant.parse("2026-10-30T00:00:00Z"))
                .claim(SecurityClaims.ROLES, List.of("STUDENT"))
                .claim(SecurityClaims.TENANT_ID, TENANT.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private JsonNode body(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return JSON.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());
    }

    private static ChatMessage message(String question, String answer, String status, String createdAt) {
        ChatMessage message = new ChatMessage();
        message.setQuestion(question);
        message.setAnswer(answer);
        message.setStatus(status);
        message.setCreatedAt(Instant.parse(createdAt));
        return message;
    }

    @Test
    void quotaMatchesTheContract() throws Exception {
        when(chatService.quota(any())).thenReturn(new ChatQuotaSnapshot(3, 17, 20, RESET_AT));

        assertThat(body(get(BASE + ChatConstants.QUOTA_PATH)).get("data")).isEqualTo(contract.get("quota"));
    }

    @Test
    void aReplyMatchesTheContract() throws Exception {
        when(chatService.send(any(), any())).thenReturn(new ChatReply("Use **present perfect**.", true, false,
                new ChatQuotaSnapshot(4, 16, 20, RESET_AT), SESSION));

        JsonNode response = body(post(BASE + ChatConstants.MESSAGES_PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + SESSION + "\",\"message\":\"Question\"}"));

        assertThat(response.get("data")).isEqualTo(contract.get("reply"));
    }

    @Test
    void theLatestSessionMatchesTheContract() throws Exception {
        when(chatService.latestSession(any())).thenReturn(new ChatLatestSession(SESSION, List.of(
                message("What is a gerund?", "A verb form ending in -ing.", "OK", "2026-10-29T03:00:00Z"),
                message("Weather today?", "I can only help with English and PTE.", "REFUSED", "2026-10-29T03:05:00Z"))));

        assertThat(body(get(BASE + ChatConstants.LATEST_SESSION_PATH)).get("data")).isEqualTo(contract.get("latestSession"));
    }

    @Test
    void theConsentStatusMatchesTheContractWhenGrantedAndWhenMissing() throws Exception {
        when(consentService.status(STUDENT)).thenReturn(
                new ChatConsentStatus("2026-10-29", true, Instant.parse("2026-10-29T10:00:00Z")));
        assertThat(body(get(BASE + ChatConstants.CONSENT_PATH)).get("data")).isEqualTo(contract.get("consentGranted"));

        when(consentService.status(STUDENT)).thenReturn(new ChatConsentStatus("2026-10-29", false, null));
        assertThat(body(get(BASE + ChatConstants.CONSENT_PATH)).get("data")).isEqualTo(contract.get("consentMissing"));
    }

    @Test
    void theQuotaExceededErrorCarriesTheCodeAndTheDataTheWebClientReads() throws Exception {
        when(chatService.send(any(), any())).thenThrow(new ChatQuotaExceededException(new ChatQuotaSnapshot(20, 0, 20, RESET_AT)));

        JsonNode response = body(post(BASE + ChatConstants.MESSAGES_PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + SESSION + "\",\"message\":\"Question\"}"));

        assertThat(response.get("code")).isEqualTo(contract.get("quotaExceededError").get("code"));
        assertThat(response.get("data")).isEqualTo(contract.get("quotaExceededError").get("data"));
        assertThat(response.get("userMessage").asString()).isNotBlank();
    }

    @Test
    void theConsentRequiredErrorCarriesTheCodeAndTheVersion() throws Exception {
        when(chatService.send(any(), any())).thenThrow(new ChatConsentRequiredException());

        JsonNode response = body(post(BASE + ChatConstants.MESSAGES_PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + SESSION + "\",\"message\":\"Question\"}"));

        assertThat(response.get("code")).isEqualTo(contract.get("consentRequiredError").get("code"));
        assertThat(response.get("data").get("policyVersion").asString())
                .isEqualTo(ChatConstants.CONSENT_POLICY_VERSION)
                .isEqualTo(contract.get("consentRequiredError").get("data").get("policyVersion").asString());
    }

    @Test
    void theExercisePromptCapIsTheOneTheWebClientAlsoUses() {
        assertThat(com.pte.chat.internal.constant.ChatConstants.EXERCISE_PROMPT_MAX_CHARS)
                .isEqualTo(contract.path("limits").path("exercisePromptMaxChars").asInt());
    }
}
