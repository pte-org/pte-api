package com.pte.scoring.internal.rubric.speech;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.AzureBandMappingCatalog;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.support.SpeakingRubrics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Azure-to-Pearson band table (phase 5, U2): versioned, every boundary tested, released as NOT calibrated
 * against human raters, and one Oral Fluency rule whether Azure returns prosody or not.
 */
class AzureBandMappingTest {

    private final AzureBandMapping version1 = AzureBandMappingCatalog.find("1").orElseThrow();

    @Test
    void theCodeDeclaresExactlyTheMappingVersionsAStoredRubricMayName() {
        assertThat(AzureBandMappingCatalog.all().stream().map(AzureBandMapping::version)
                .collect(Collectors.toSet())).isEqualTo(RubricConstants.SUPPORTED_AZURE_BAND_MAPPING_VERSIONS);
    }

    @Test
    void versionOneIsUncalibratedAndHasFiveAscendingThresholdsPerTrait() {
        assertThat(version1.version()).isEqualTo(RubricConstants.AZURE_BAND_MAPPING_VERSION_1);
        assertThat(version1.calibrated()).isFalse();
        assertThat(version1.pronunciation()).extracting(AzureBandThreshold::band).containsExactly(1, 2, 3, 4, 5);
        assertThat(version1.oralFluency()).extracting(AzureBandThreshold::band).containsExactly(1, 2, 3, 4, 5);
        assertThat(version1.pronunciation()).extracting(AzureBandThreshold::minAzureScore)
                .containsExactly(30.0, 45.0, 60.0, 75.0, 90.0);
    }

    @ParameterizedTest(name = "accuracy {0} -> band {1}")
    @CsvSource({"0,0", "29.99,0", "30,1", "44.99,1", "45,2", "59.99,2", "60,3", "74.99,3", "75,4", "89.99,4",
            "90,5", "100,5"})
    void everyPronunciationBoundary(double accuracy, int band) {
        assertThat(version1.pronunciationBand(accuracy)).isEqualTo(band);
    }

    @ParameterizedTest(name = "fluency {0} -> band {1}")
    @CsvSource({"0,0", "29.99,0", "30,1", "44.99,1", "45,2", "59.99,2", "60,3", "74.99,3", "75,4", "89.99,4",
            "90,5", "100,5"})
    void everyOralFluencyBoundaryWithoutProsody(double fluency, int band) {
        assertThat(version1.oralFluencyBand(fluency, null)).isEqualTo(band);
    }

    @Test
    void oralFluencyAveragesFluencyAndProsodyWhenProsodyExistsAndUsesFluencyAloneOtherwise() {
        assertThat(AzureBandMapping.oralFluencyInput(80.0, 70.0)).isEqualTo(75.0);
        assertThat(version1.oralFluencyBand(80.0, 70.0)).isEqualTo(4);
        assertThat(AzureBandMapping.oralFluencyInput(80.0, null)).isEqualTo(80.0);
        assertThat(version1.oralFluencyBand(80.0, null)).isEqualTo(4);
        // A missing prosody is never a prosody of 0 (that would be band 1 here).
        assertThat(version1.oralFluencyBand(80.0, 0.0)).isEqualTo(1);
    }

    @Test
    void aMissingAzureScoreCountsAsZero() {
        assertThat(version1.pronunciationBand(null)).isZero();
        assertThat(version1.oralFluencyBand(null, null)).isZero();
    }

    @Test
    void anUnknownMappingVersionFailsClosed() {
        assertThat(AzureBandMappingCatalog.find("99")).isEmpty();
        assertThat(AzureBandMappingCatalog.find(null)).isEmpty();
        assertThatThrownBy(() -> SpeechBands.mappingOf(SpeakingRubrics.withMapping(
                ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, "99")))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> assertThat(ex.getMessage())
                        .isEqualTo(ScoringConstants.AI_AZURE_BAND_MAPPING_UNKNOWN));
    }

    @Test
    void speechBandsRecordTheRawScoresTheMappingAndAMissingProsody() {
        SpeechBands bands = SpeechBands.of(version1, 91.0, 62.0, null);

        assertThat(bands.pronunciation()).isEqualTo(5);
        assertThat(bands.oralFluency()).isEqualTo(3);
        assertThat(bands.prosodyUnavailable()).isTrue();
        assertThat(bands.traitsWithContent(4)).containsExactly(java.util.Map.entry("CONTENT", 4),
                java.util.Map.entry("PRONUNCIATION", 5), java.util.Map.entry("ORAL_FLUENCY", 3));
        java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
        bands.writeInto(detail);
        assertThat(detail).containsEntry(SpeechBands.PROSODY_UNAVAILABLE, true);
        assertThat(detail.get(SpeechBands.SPEECH_DETAIL)).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("accuracy", 91.0)
                .containsEntry("oralFluencyInput", 62.0)
                .containsEntry("azureBandMapping", java.util.Map.of("version", 1, "calibrated", false));
    }
}
