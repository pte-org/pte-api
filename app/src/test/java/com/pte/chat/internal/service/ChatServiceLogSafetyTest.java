package com.pte.chat.internal.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pte.chat.internal.client.ChatAiException;
import com.pte.chat.internal.client.ChatAiFailureReason;
import com.pte.chat.internal.exception.ChatBusyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * A failing database or provider call can carry the failing statement and its values, which may hold the
 * student's question. Nothing of that may reach the log: not the message, not an argument, not the stack trace.
 */
class ChatServiceLogSafetyTest extends ChatServiceTestSupport {

    private static final String SECRET = "SECRET-MARKER-9f3a";
    private static final String QUESTION = "My private question about the present perfect tense";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void captureLog() {
        logger = (Logger) LoggerFactory.getLogger(ChatService.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    private static RuntimeException leakyDatabaseError() {
        SQLException sql = new SQLException("ERROR: null value in column \"answer\" Failing row contains ("
                + SECRET + ", " + QUESTION + ")", "23502");
        return new DataIntegrityViolationException("could not execute statement; Failing row contains ("
                + SECRET + ", " + QUESTION + ")", sql);
    }

    private static RuntimeException leakyRuntimeError() {
        return new IllegalStateException("provider said: " + SECRET + " / " + QUESTION,
                new IllegalArgumentException("inner " + SECRET + " " + QUESTION));
    }

    private static void collect(IThrowableProxy proxy, List<String> into) {
        for (IThrowableProxy current = proxy; current != null; current = current.getCause()) {
            into.add(String.valueOf(current.getMessage()));
            into.add(String.valueOf(current.getClassName()));
            for (StackTraceElementProxy frame : current.getStackTraceElementProxyArray()) {
                into.add(frame.getStackTraceElement().toString());
            }
            for (IThrowableProxy suppressed : current.getSuppressed()) {
                collect(suppressed, into);
            }
        }
    }

    private List<String> everythingLogged() {
        List<String> texts = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            texts.add(String.valueOf(event.getMessage()));
            texts.add(String.valueOf(event.getFormattedMessage()));
            if (event.getArgumentArray() != null) {
                Arrays.stream(event.getArgumentArray()).forEach(argument -> texts.add(String.valueOf(argument)));
            }
            collect(event.getThrowableProxy(), texts);
        }
        return texts;
    }

    private void assertSomethingWasLoggedButNoSecrets() {
        assertThat(appender.list).as("the failure is logged").isNotEmpty();
        assertThat(everythingLogged()).noneSatisfy(text -> assertThat(text).contains(SECRET));
        assertThat(everythingLogged()).noneSatisfy(text -> assertThat(text).contains(QUESTION));
        assertThat(everythingLogged()).noneSatisfy(text -> assertThat(text).contains("Failing row"));
    }

    // ---- other failure paths ------------------------------------------------------------------------------

    @Test
    void anUnexpectedProviderExceptionLogsNoStudentText() {
        when(client.generate(any(), any(), any())).thenThrow(leakyRuntimeError());

        assertThatThrownBy(() -> service.send(caller, command(QUESTION))).isInstanceOf(ChatBusyException.class);

        assertSomethingWasLoggedButNoSecrets();
        assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("IllegalStateException").contains("IllegalArgumentException"));
    }

    @Test
    void aProviderFailureLogsItsReasonAndStatusSoAnOperatorCanFixIt() {
        when(client.generate(any(), any(), any())).thenThrow(
                new ChatAiException(ChatAiFailureReason.BAD_REQUEST, "Provider returned HTTP status 404"));

        assertThatThrownBy(() -> service.send(caller, command(QUESTION))).isInstanceOf(ChatBusyException.class);

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("BAD_REQUEST").contains("404");
        });
        assertThat(everythingLogged()).noneSatisfy(text -> assertThat(text).contains(QUESTION));
    }

    @Test
    void aFailingRefundLogsNoStudentText() {
        when(client.generate(any(), any(), any())).thenReturn(offTopicAnswer());
        doThrow(leakyDatabaseError()).when(quotaService).refundQuietly(any());

        service.send(caller, command(QUESTION));

        assertSomethingWasLoggedButNoSecrets();
    }
}
