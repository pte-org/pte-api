package com.pte.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One source of truth for an AI task's item score and Form band: {@code RubricScorer} and {@code FormRule.bandFor}.
 * (a) No vendor scorer averages sub-scores itself; (b) no word-count band threshold lives outside the rubric
 * catalog. Known offenders sit in the ratchet allowlist, which may only shrink (plan quang-scoring-rubric-transparency,
 * phases 3 and 5 empty it).
 */
class RubricScoreSingleSourceTest {

    private static final String ALLOWLIST = "rubric-single-source.allowlist";
    private static final String AVERAGE_PREFIX = "average:";
    private static final String BAND_PREFIX = "form-band:";
    private static final String VENDOR_DIR = "com/pte/scoring/internal/vendor/";
    private static final String CATALOG_DIR = "com/pte/scoring/internal/rubric/catalog/";

    private static final String BAND_LITERAL = "(?:120|199|200|300|301|380|40|49|50|70|71|100)";
    private static final String WORD_NAME = "(?:\\w+\\.)*\\w*(?i:words?)\\w*(?:\\(\\))?";
    private static final String COMPARATOR = "\\s*(?:<=|>=|==|!=|<|>)\\s*";
    private static final Pattern AVERAGE = Pattern.compile("\\.average\\s*\\(\\s*\\)");
    private static final Pattern WORD_COMPARED_TO_BAND = Pattern.compile(
            "\\b" + WORD_NAME + COMPARATOR + BAND_LITERAL + "\\b|\\b" + BAND_LITERAL + COMPARATOR + WORD_NAME);
    private static final Pattern BAND_CONSTANT = Pattern.compile(
            "\\bint\\s+\\w*(?:WORDS|BELOW|ABOVE)\\w*\\s*=\\s*" + BAND_LITERAL + "\\s*;");

    @Test
    void itemScoresAndFormBandsHaveOneSource() {
        Map<String, Integer> measured = new TreeMap<>();
        for (Path source : SourceDebtAllowlist.javaSources()) {
            String file = SourceDebtAllowlist.key(source);
            boolean scoreConsumer = file.startsWith(VENDOR_DIR)
                    || file.startsWith("com/pte/scoring/internal/rubric/service/RubricSimulation");
            int averages = scoreConsumer ? countAverages(SourceDebtAllowlist.lines(source)) : 0;
            int bands = file.startsWith(CATALOG_DIR) ? 0 : countBandThresholds(SourceDebtAllowlist.lines(source));
            if (averages > 0) {
                measured.put(AVERAGE_PREFIX + file, averages);
            }
            if (bands > 0) {
                measured.put(BAND_PREFIX + file, bands);
            }
        }

        assertThat(SourceDebtAllowlist.ratchet(ALLOWLIST, SourceDebtAllowlist.load(ALLOWLIST), measured, 0,
                "score through RubricScorer and band through FormRule.bandFor"))
                .isEmpty();
    }

    @Test
    void detectorFlagsWordCountBandComparisonsAndConstants() {
        assertThat(countBandThresholds(java.util.List.of("if (words >= 200 && words <= 300) {"))).isEqualTo(1);
        assertThat(countBandThresholds(java.util.List.of("boolean ok = wordCount < 120;"))).isEqualTo(1);
        assertThat(countBandThresholds(java.util.List.of("if (380 >= text.wordCount()) {"))).isEqualTo(1);
        assertThat(countBandThresholds(java.util.List.of("private static final int SST_INVALID_BELOW = 40;")))
                .isEqualTo(1);
        assertThat(countBandThresholds(java.util.List.of("private static final int ESSAY_MAX_WORDS = 300;")))
                .isEqualTo(1);
    }

    @Test
    void detectorIgnoresUnrelatedNumbersAndComments() {
        assertThat(countBandThresholds(java.util.List.of("private static final int QUEUE_CAPACITY = 200;"))).isZero();
        assertThat(countBandThresholds(java.util.List.of("if (status >= 200 && status < 300) {"))).isZero();
        assertThat(countBandThresholds(java.util.List.of("    // words >= 200 is full form"))).isZero();
        assertThat(countBandThresholds(java.util.List.of(" * Essay: 200-300 words, wordCount < 120"))).isZero();
        assertThat(countAverages(java.util.List.of("double a = list.stream().average().orElse(0);"))).isEqualTo(1);
        assertThat(countAverages(java.util.List.of("// parts.average()"))).isZero();
    }

    @Test
    void simulatorDelegatesScoreAndFormWithoutProvidersOrWrites() throws java.io.IOException {
        Path source = SourceDebtAllowlist.javaSources().stream()
                .filter(path -> path.getFileName().toString().equals("RubricSimulationService.java")).findFirst().orElseThrow();
        String code = java.nio.file.Files.readString(source);
        assertThat(code).contains("RubricScorer.score(", ".bandFor(", "@Transactional(readOnly = true)");
        assertThat(code).doesNotContain(".save(", ".saveAndFlush(", ".delete(", "internal.vendor", "internal.messaging");
        assertThat(java.util.Arrays.stream(com.pte.scoring.internal.rubric.service.RubricSimulationService.class
                .getDeclaredConstructors()[0].getParameterTypes()).map(Class::getSimpleName))
                .containsExactly("ScoringRubricVersionRepository", "RubricResolver");
    }

    private static int countAverages(java.util.List<String> lines) {
        return (int) code(lines).filter(line -> AVERAGE.matcher(line).find()).count();
    }

    private static int countBandThresholds(java.util.List<String> lines) {
        return (int) code(lines).filter(line -> WORD_COMPARED_TO_BAND.matcher(line).find()
                || BAND_CONSTANT.matcher(line).find()).count();
    }

    private static java.util.stream.Stream<String> code(java.util.List<String> lines) {
        return lines.stream().map(String::strip)
                .filter(line -> !line.startsWith("//") && !line.startsWith("*") && !line.startsWith("/*"));
    }
}
