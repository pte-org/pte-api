package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PictureSettingsTest {

    private static final PictureSettings DEFAULTS = new PictureSettings(2, true);

    private static PromptContent withSettings(String... rows) {
        return new PromptContent("Picture", List.of(Block.text("", "Paint it.")), List.of(
                new PromptContent.Section("image-settings", "Image Settings", List.of(Block.fields("", List.of(rows))))));
    }

    @Test
    void theShippedPicturePromptCarriesItsSettingsButTheWriterNeverSeesThem() {
        PromptContent shipped = PromptTestRegistry.codec().read(PromptTestRegistry
                .find(AiPromptKind.GENERATOR, "DESCRIBE_IMAGE_PICTURE").orElseThrow().getContent());

        assertThat(PictureSettings.from(Optional.of(shipped), new PictureSettings(9, false)))
                .isEqualTo(new PictureSettings(2, false));
        assertThat(PromptTextAssembler.assemble(PictureSettings.withoutSettings(shipped)))
                .doesNotContain("maxImageAttempts");
    }

    @Test
    void editedValuesAreReadAndBadOnesKeepTheDefaults() {
        assertThat(PictureSettings.from(Optional.of(withSettings("maxImageAttempts: 4", "whenImageFails: ASK")),
                DEFAULTS)).isEqualTo(new PictureSettings(4, false));
        assertThat(PictureSettings.from(Optional.of(withSettings("maxImageAttempts: 9", "whenImageFails: CHART")),
                DEFAULTS)).isEqualTo(new PictureSettings(2, true));
        assertThat(PictureSettings.from(Optional.of(withSettings("maxImageAttempts: 9")), DEFAULTS))
                .isEqualTo(DEFAULTS);
        assertThat(PictureSettings.from(Optional.empty(), DEFAULTS)).isEqualTo(DEFAULTS);
    }

    @Test
    void savingOutOfRangeValuesIsRefusedOnlyForThePicturePrompt() {
        PromptContent bad = withSettings("maxImageAttempts: 0", "whenImageFails: RETRY");

        assertThat(PictureSettings.problems("DESCRIBE_IMAGE_PICTURE", bad)).hasSize(2);
        assertThat(PictureSettings.problems("DESCRIBE_IMAGE", bad)).isEmpty();
    }
}
