package com.pte.overview.internal.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pte.overview.internal.dto.response.DashboardSection;
import com.pte.overview.internal.dto.response.SectionStatus;
import com.pte.shared.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardSectionRunnerTest {

    private final DashboardSectionRunner runner = new DashboardSectionRunner();
    private final Logger runnerLogger = (Logger) LoggerFactory.getLogger(DashboardSectionRunner.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        runnerLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        runnerLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void successfulLoaderYieldsOkSectionWithData() {
        DashboardSection<String> section = runner.run("learners", () -> "payload");

        assertThat(section.status()).isEqualTo(SectionStatus.OK);
        assertThat(section.data()).isEqualTo("payload");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void failingLoaderIsDegradedToErrorSectionWithoutData() {
        DashboardSection<String> section = runner.run("attempts", () -> {
            throw new IllegalStateException("db exploded: password=hunter2");
        });

        assertThat(section.status()).isEqualTo(SectionStatus.ERROR);
        assertThat(section.data()).isNull();
        // No raw error text may travel in the section object.
        assertThat(section.toString()).doesNotContain("hunter2").doesNotContain("exploded");
    }

    @Test
    void failureIsLoggedWithSectionNameCorrelationIdAndTheCause() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-abc-123");

        runner.run("exams", () -> {
            throw new IllegalArgumentException("boom");
        });

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("exams").contains("corr-abc-123");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void oneFailingSectionDoesNotStopTheNextOnesFromRunning() {
        AtomicInteger executed = new AtomicInteger();

        DashboardSection<Integer> first = runner.run("a", () -> {
            executed.incrementAndGet();
            throw new RuntimeException("first fails");
        });
        DashboardSection<Integer> second = runner.run("b", () -> {
            executed.incrementAndGet();
            return 2;
        });

        assertThat(executed).hasValue(2);
        assertThat(first.status()).isEqualTo(SectionStatus.ERROR);
        assertThat(second.status()).isEqualTo(SectionStatus.OK);
        assertThat(second.data()).isEqualTo(2);
    }

    @Test
    void jvmErrorsAreNotSwallowedAsASectionFailure() {
        assertThatThrownBy(() -> runner.run("x", () -> {
            throw new OutOfMemoryError("not a section problem");
        })).isInstanceOf(OutOfMemoryError.class);
    }
}
