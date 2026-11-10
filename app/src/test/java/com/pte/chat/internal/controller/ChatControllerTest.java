package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.internal.service.ChatHistoryEntry;
import com.pte.chat.internal.service.ChatReply;
import com.pte.chat.internal.service.ChatSendCommand;
import com.pte.chat.internal.service.ChatService;
import com.pte.chat.internal.service.quota.ChatQuotaSnapshot;
import com.pte.shared.exception.DomainException;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.ResourceServerJwt;
import com.pte.shared.security.SecurityClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the two chat endpoints with {@link ChatService} mocked: the real controller behind the real
 * method-security proxy and the real {@link GlobalExceptionHandler}, as the other controller tests in this repo do.
 */
class ChatControllerTest {

    private static final String MESSAGES = ChatConstants.CHAT_PATH + ChatConstants.MESSAGES_PATH;
    private static final String QUOTA = ChatConstants.CHAT_PATH + ChatConstants.QUOTA_PATH;
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant RESET_AT = Instant.parse("2026-10-28T17:00:00Z");

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private final ChatService chatService = mock(ChatService.class);
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        // the controller exists only while chat is enabled (plan AD-11)
        TestPropertyValues.of(ChatConstants.PROPERTY_ENABLED + "=true").applyTo(context);
        context.register(MethodSecurityConfig.class);
        context.registerBean(ChatController.class, () -> new ChatController(chatService));
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ChatController.class))
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
                .issuedAt(Instant.parse("2026-10-28T00:00:00Z")).expiresAt(Instant.parse("2026-10-29T00:00:00Z"))
                .claim(SecurityClaims.ROLES, List.of(roles))
                .claim(SecurityClaims.TENANT_ID, TENANT.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(ResourceServerJwt.rolesConverter().convert(jwt));
    }

    private static String body(String message) {
        return "{\"message\":" + (message == null ? "null" : "\"" + message + "\"") + "}";
    }

    private static ChatReply reply(String markdown, boolean onTopic, int remaining) {
        return new ChatReply(markdown, onTopic, new ChatQuotaSnapshot(20 - remaining, remaining, 20, RESET_AT));
    }

    // ---- POST /messages ------------------------------------------------------------------------------------

    @Test
    void sendingAMessagePassesTheCallerAndTheRequestToTheService() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("answer", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"message\":\"How do I use it?\",\"taskType\":\"WRITE_ESSAY\",\"language\":\"vi\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CurrentUser> caller = ArgumentCaptor.forClass(CurrentUser.class);
        ArgumentCaptor<ChatSendCommand> command = ArgumentCaptor.forClass(ChatSendCommand.class);
        verify(chatService).send(caller.capture(), command.capture());
        assertThat(caller.getValue().userId()).isEqualTo(STUDENT);
        assertThat(caller.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(command.getValue()).isEqualTo(new ChatSendCommand("How do I use it?", "WRITE_ESSAY", "vi"));
    }

    @Test
    void theExercisePromptIsPassedAlongWhenSent() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("answer", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"message\":\"How do I say it?\",\"taskType\":\"WRITE_ESSAY\",\"language\":\"en\","
                                + "\"exercisePrompt\":\"Technology makes us less social.\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatSendCommand> command = ArgumentCaptor.forClass(ChatSendCommand.class);
        verify(chatService).send(any(), command.capture());
        assertThat(command.getValue().exercisePrompt()).isEqualTo("Technology makes us less social.");
    }

    @Test
    void theEarlierTurnsOfTheConversationArePassedAlongInOrder() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("answer", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"message\":\"And the past tense?\",\"history\":["
                                + "{\"role\":\"user\",\"text\":\"present perfect?\"},"
                                + "{\"role\":\"assistant\",\"text\":\"use has or have\"}]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatSendCommand> command = ArgumentCaptor.forClass(ChatSendCommand.class);
        verify(chatService).send(any(), command.capture());
        assertThat(command.getValue().history()).containsExactly(
                new ChatHistoryEntry("user", "present perfect?"), new ChatHistoryEntry("assistant", "use has or have"));
    }

    @Test
    void aRequestWithoutHistoryPassesNone() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("answer", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatSendCommand> command = ArgumentCaptor.forClass(ChatSendCommand.class);
        verify(chatService).send(any(), command.capture());
        assertThat(command.getValue().history()).isNull();
    }

    @Test
    void aHistoryWithTooManyEntriesIs400AndTheServiceIsNotCalled() throws Exception {
        String entries = String.join(",", java.util.Collections.nCopies(ChatConstants.HISTORY_MAX_ITEMS + 1,
                "{\"role\":\"user\",\"text\":\"q\"}"));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Question\",\"history\":[" + entries + "]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void taskTypeAndLanguageAreOptional() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("answer", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatSendCommand> command = ArgumentCaptor.forClass(ChatSendCommand.class);
        verify(chatService).send(any(), command.capture());
        assertThat(command.getValue().taskType()).isNull();
        assertThat(command.getValue().language()).isNull();
    }

    @Test
    void aSuccessfulReplyHasTheEnvelopeTheMarkdownTheFlagAndTheQuota() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("**Use** has/have + V3.", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.markdown").value("**Use** has/have + V3."))
                .andExpect(jsonPath("$.data.onTopic").value(true))
                .andExpect(jsonPath("$.data.remaining").value(19))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.resetAt").value("2026-10-28T17:00:00Z"));
    }

    @Test
    void anOffTopicRefusalKeepsItsFlag() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("refusal text", false, 20));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(jsonPath("$.data.onTopic").value(false))
                .andExpect(jsonPath("$.data.remaining").value(20));
    }

    @Test
    void theReplyExposesNoInternalFieldsAndNoSession() throws Exception {
        when(chatService.send(any(), any())).thenReturn(reply("answer", true, 19));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(jsonPath("$.data.used").doesNotExist())
                .andExpect(jsonPath("$.data.quota").doesNotExist())
                .andExpect(jsonPath("$.data.tokensIn").doesNotExist())
                .andExpect(jsonPath("$.data.model").doesNotExist())
                .andExpect(jsonPath("$.data.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.fromCache").doesNotExist());
    }

    // ---- request validation --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\\n\\t"})
    void aBlankMessageIs400AndTheServiceIsNotCalled(String message) throws Exception {
        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body(message)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(chatService);
    }

    @Test
    void aMissingOrNullMessageIs400() throws Exception {
        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content("{\"message\":"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void aBodyWithoutAJsonContentTypeIs415() throws Exception {
        mvc.perform(post(MESSAGES).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(chatService);
    }

    // ---- error mapping -------------------------------------------------------------------------------------

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new ChatQuotaExceededException(new ChatQuotaSnapshot(20, 0, 20, RESET_AT)), 429,
                        "CHAT_QUOTA_EXCEEDED"),
                Arguments.of(new ChatRequestInFlightException(), 409, "CHAT_REQUEST_IN_FLIGHT"),
                Arguments.of(new ChatBusyException(), 503, "CHAT_BUSY"),
                Arguments.of(new ChatInvalidRequestException("message is too long"), 400, "CHAT_INVALID_REQUEST"));
    }

    @ParameterizedTest
    @MethodSource("serviceErrors")
    void eachChatErrorKeepsItsStatusItsStableCodeAndItsDisplayCopy(DomainException error, int httpStatus, String code)
            throws Exception {
        when(chatService.send(any(), any())).thenThrow(error);

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.userMessage").value(error.getUserMessage()));

        assertThat(error.getUserMessage()).isNotBlank();
    }

    @Test
    void theQuotaExceededBodyCarriesResetAtLimitAndZeroRemaining() throws Exception {
        when(chatService.send(any(), any())).thenThrow(
                new ChatQuotaExceededException(new ChatQuotaSnapshot(20, 0, 20, RESET_AT)));

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ChatConstants.ERR_QUOTA_EXCEEDED))
                .andExpect(jsonPath("$.data.resetAt").value("2026-10-28T17:00:00Z"))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.remaining").value(0));
    }

    @Test
    void theOtherChatErrorsCarryNoData() throws Exception {
        when(chatService.send(any(), any())).thenThrow(new ChatBusyException());

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    // ---- GET /quota ----------------------------------------------------------------------------------------

    @Test
    void theQuotaEndpointReturnsUsedRemainingLimitAndResetAt() throws Exception {
        when(chatService.quota(any())).thenReturn(new ChatQuotaSnapshot(5, 15, 20, RESET_AT));

        mvc.perform(get(QUOTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.used").value(5))
                .andExpect(jsonPath("$.data.remaining").value(15))
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.resetAt").value("2026-10-28T17:00:00Z"));

        ArgumentCaptor<CurrentUser> caller = ArgumentCaptor.forClass(CurrentUser.class);
        verify(chatService).quota(caller.capture());
        assertThat(caller.getValue().userId()).isEqualTo(STUDENT);
    }

    // ---- who may call --------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "HOST_ADMIN", "ACADEMIC_STAFF", "ACADEMIC_MANAGER"})
    void aCallerWithoutTheStudentRoleIs403OnEveryEndpointAndNothingReachesTheService(String role) throws Exception {
        authenticate(role);

        mvc.perform(post(MESSAGES).contentType(MediaType.APPLICATION_JSON).content(body("Question")))
                .andExpect(status().isForbidden());
        mvc.perform(get(QUOTA)).andExpect(status().isForbidden());

        verifyNoInteractions(chatService);
    }

    @Test
    void aStudentIsLetIn() throws Exception {
        authenticate("STUDENT");
        when(chatService.quota(any())).thenReturn(new ChatQuotaSnapshot(0, 20, 20, RESET_AT));

        mvc.perform(get(QUOTA)).andExpect(status().isOk());

        verify(chatService).quota(any());
        verify(chatService, never()).send(any(), any());
    }

    @Test
    void theControllerIsRestrictedToTheStudentRoleAtClassLevel() {
        org.springframework.security.access.prepost.PreAuthorize annotation = org.springframework.core.annotation
                .AnnotationUtils.findAnnotation(ChatController.class,
                        org.springframework.security.access.prepost.PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('STUDENT')");
    }
}
