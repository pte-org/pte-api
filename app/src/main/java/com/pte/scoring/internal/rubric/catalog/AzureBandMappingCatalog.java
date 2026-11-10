package com.pte.scoring.internal.rubric.catalog;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.speech.AzureBandMapping;
import com.pte.scoring.internal.rubric.speech.AzureBandThreshold;

import java.util.List;
import java.util.Optional;

/**
 * The Azure-to-Pearson band mapping tables declared in code, by version. A rubric version stores the number of the
 * table it uses ({@code azureBandMappingVersion}), so a later table is added here as a new version and never edits an
 * existing one. Version 1 is the team's first proposal: evenly spaced thresholds, released as NOT calibrated against
 * human raters ("chưa hiệu chỉnh với dữ liệu người chấm"). The same thresholds serve Pronunciation and Oral Fluency
 * until rater data says otherwise.
 */
public final class AzureBandMappingCatalog {

    private static final List<AzureBandThreshold> VERSION_1_THRESHOLDS = List.of(
            new AzureBandThreshold(1, 30), new AzureBandThreshold(2, 45), new AzureBandThreshold(3, 60),
            new AzureBandThreshold(4, 75), new AzureBandThreshold(5, 90));

    private static final List<AzureBandMapping> MAPPINGS = List.of(
            new AzureBandMapping(RubricConstants.AZURE_BAND_MAPPING_VERSION_1, false, VERSION_1_THRESHOLDS,
                    VERSION_1_THRESHOLDS));

    private AzureBandMappingCatalog() {
    }

    public static List<AzureBandMapping> all() {
        return MAPPINGS;
    }

    /** The table a rubric version names; its parameters carry the version number as text. */
    public static Optional<AzureBandMapping> find(String version) {
        if (version == null) {
            return Optional.empty();
        }
        return MAPPINGS.stream().filter(mapping -> String.valueOf(mapping.version()).equals(version.strip()))
                .findFirst();
    }
}
