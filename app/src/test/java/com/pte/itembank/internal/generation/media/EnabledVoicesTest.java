package com.pte.itembank.internal.generation.media;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnabledVoicesTest {

    static EnabledVoices enabledWith(String disabled, Environment environment) {
        return new EnabledVoices(VoicePickerTest.propertiesWithDisabled(disabled), environment);
    }

    @Test
    void everyCatalogVoiceIsEnabledByDefault() {
        var enabled = enabledWith("", new MockEnvironment());
        assertEquals(KokoroVoiceCatalog.all().size(), enabled.all().size());
    }

    @Test
    void aDisabledVoiceLeavesTheList() {
        var enabled = enabledWith("af_bella", new MockEnvironment());
        assertTrue(enabled.find("af_bella").isEmpty());
        assertTrue(enabled.find("af_heart").isPresent());
        assertEquals(KokoroVoiceCatalog.all().size() - 1, enabled.all().size());
    }

    @Test
    void spacesAndEmptyEntriesInTheListAreIgnored() {
        var enabled = enabledWith(" af_bella , ,bm_george,", new MockEnvironment());
        assertTrue(enabled.find("af_bella").isEmpty());
        assertTrue(enabled.find("bm_george").isEmpty());
        assertEquals(KokoroVoiceCatalog.all().size() - 2, enabled.all().size());
    }

    @Test
    void anUnknownCodeChangesNothingAndIsWarnedAboutOnce() {
        var warnings = captureWarnings(() -> {
            var enabled = enabledWith("not_a_voice", new MockEnvironment());
            assertEquals(KokoroVoiceCatalog.all().size(), enabled.all().size());
        });
        assertEquals(1, warnings.stream().filter(line -> line.contains("not_a_voice")).count());
    }

    @Test
    void retiredSettingsDoNotStopTheStartUpButAreWarnedAbout() {
        var environment = new MockEnvironment().withProperty("QUESTION_GEN_VOICES_US", "af_heart");
        var warnings = captureWarnings(() ->
                assertEquals(KokoroVoiceCatalog.all().size(), enabledWith("", environment).all().size()));
        assertEquals(1, warnings.stream().filter(line -> line.contains("QUESTION_GEN_VOICES_US")).count());
    }

    @Test
    void switchingOneAccentOffIsWarnedAboutAndTheOtherAccentStays() {
        String uk = String.join(",", KokoroVoiceCatalog.all().stream()
                .filter(voice -> voice.accent() == KokoroVoiceCatalog.Accent.UK)
                .map(KokoroVoiceCatalog.Voice::code).toList());
        var warnings = captureWarnings(() -> {
            var enabled = enabledWith(uk, new MockEnvironment());
            assertTrue(enabled.of(KokoroVoiceCatalog.Accent.UK).isEmpty());
            assertFalse(enabled.of(KokoroVoiceCatalog.Accent.US).isEmpty());
        });
        assertEquals(1, warnings.stream().filter(line -> line.contains("UK")).count());
    }

    private static List<String> captureWarnings(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(EnabledVoices.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void switchingEveryVoiceOffIsRefused() {
        String all = String.join(",", KokoroVoiceCatalog.all().stream().map(KokoroVoiceCatalog.Voice::code).toList());
        assertThrows(IllegalStateException.class, () -> enabledWith(all, new MockEnvironment()));
    }
}
