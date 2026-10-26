package com.pte.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code docs/CODING_STANDARDS_API.md}: a source file holds at most 300 lines. */
class SourceFileSizeTest {

    private static final String ALLOWLIST = "oversized-files.allowlist";
    private static final int MAX_LINES = 300;

    @Test
    void sourceFilesStayWithinTheLineLimit() {
        Map<String, Integer> linesPerFile = new TreeMap<>();
        for (Path source : SourceDebtAllowlist.javaSources()) {
            linesPerFile.put(SourceDebtAllowlist.key(source), SourceDebtAllowlist.lines(source).size());
        }

        assertThat(SourceDebtAllowlist.ratchet(ALLOWLIST, SourceDebtAllowlist.load(ALLOWLIST), linesPerFile,
                MAX_LINES, "split the file")).isEmpty();
    }
}
