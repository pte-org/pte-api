package com.pte.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Time must come from the injected {@link java.time.Clock} so tests can pin it. Counted as a direct read:
 * {@code now()} / {@code now(zone)} and {@code ::now} on the java.time types, {@code System.currentTimeMillis()},
 * {@code new Date()} and {@code Calendar.getInstance()}; {@code now(clock)} is fine. Comments and string literals
 * are ignored. Out of scope on purpose: {@code Clock.systemUTC()} defaults (the bean in {@code ClockConfig} and
 * constructor fallbacks), which pick the clock rather than read it.
 */
class NoDirectClockAccessTest {

    private static final String ALLOWLIST = "direct-clock-access.allowlist";
    private static final int NO_DEBT = 0;
    private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
            "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|/\\*[\\s\\S]*?\\*/|//[^\\n]*");
    private static final String TIME_TYPE =
            "\\b(?:Instant|LocalDateTime|LocalDate|LocalTime|OffsetDateTime|ZonedDateTime|Year|YearMonth|MonthDay)";
    private static final Pattern DIRECT_CLOCK_READ = Pattern.compile(
            TIME_TYPE + "\\s*\\.\\s*now\\s*\\((?!\\s*[\\w.]*(?i:clock)\\s*\\))"
                    + "|" + TIME_TYPE + "\\s*::\\s*now\\b"
                    + "|\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\("
                    + "|\\bnew\\s+(?:java\\.util\\.)?Date\\s*\\(\\s*\\)"
                    + "|\\bCalendar\\s*\\.\\s*getInstance\\s*\\(");

    @Test
    void productionCodeReadsTimeOnlyThroughTheInjectedClock() {
        assertThat(SourceDebtAllowlist.ratchet(ALLOWLIST, SourceDebtAllowlist.load(ALLOWLIST), directReadsPerFile(),
                NO_DEBT, "inject java.time.Clock instead")).isEmpty();
    }

    @Test
    void detectionIgnoresCommentsAndClockArgumentsButCatchesZoneArguments() {
        assertThat(directReads("// Instant.now() \n String s = \"LocalDate.now()\"; Instant.now(clock);"))
                .isZero();
        assertThat(directReads("LocalDate.now(ZoneId.of(\"UTC\")); Instant.now( ); Supplier<Instant> s = Instant::now;"))
                .isEqualTo(3);
    }

    private static Map<String, Integer> directReadsPerFile() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Path source : SourceDebtAllowlist.javaSources()) {
            counts.put(SourceDebtAllowlist.key(source), directReads(String.join("\n", SourceDebtAllowlist.lines(source))));
        }
        return counts;
    }

    private static int directReads(String code) {
        Matcher matcher = DIRECT_CLOCK_READ.matcher(COMMENTS_AND_LITERALS.matcher(code).replaceAll(""));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
