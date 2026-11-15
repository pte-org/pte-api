package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.itembank.internal.generation.review.ReviewPromptDefaults;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptRequirementUpgraderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneOffset.UTC);
    private final PromptContentCodec codec = PromptTestRegistry.codec();
    private final AiPromptVersionRepository versions = mock(AiPromptVersionRepository.class);
    private final PromptRequirementUpgrader upgrader = new PromptRequirementUpgrader(versions, codec, CLOCK);

    private void live(AiPromptKind kind, String key, PromptContent content) {
        AiPromptVersion version = AiPromptVersion.seeded(kind, key, codec.write(content), codec.hash(content),
                CLOCK.instant());
        when(versions.findByKindAndPromptKeyAndStatus(kind, key, AiPromptStatus.ACTIVE))
                .thenReturn(Optional.of(version));
        when(versions.highestVersionNo(kind.name(), key)).thenReturn(3);
    }

    @Test
    void anEditedReviewerWithoutTheHintSectionGetsItAsANewLiveVersion() {
        PromptContent shipped = ReviewPromptDefaults.content();
        PromptContent edited = new PromptContent("My reviewer", shipped.intro(), shipped.sections().stream()
                .filter(section -> !section.id().equals("student-hints")).toList());
        live(AiPromptKind.REVIEWER, "REVIEWER", edited);

        assertThat(upgrader.upgrade(AiPromptKind.REVIEWER, "REVIEWER", shipped)).isEqualTo(1);

        verify(versions).retireActive(AiPromptKind.REVIEWER, "REVIEWER", CLOCK.instant());
        ArgumentCaptor<AiPromptVersion> saved = ArgumentCaptor.forClass(AiPromptVersion.class);
        verify(versions).save(saved.capture());
        assertThat(saved.getValue().getVersionNo()).isEqualTo(4);
        assertThat(saved.getValue().getStatus()).isEqualTo(AiPromptStatus.ACTIVE);
        PromptContent upgraded = codec.read(saved.getValue().getContent());
        assertThat(upgraded.title()).isEqualTo("My reviewer");
        assertThat(upgraded.sections()).extracting(PromptContent.Section::id).endsWith("student-hints");
    }

    @Test
    void aPromptThatAlreadyKeepsTheRequiredTextIsLeftAlone() {
        live(AiPromptKind.REVIEWER, "REVIEWER", ReviewPromptDefaults.content());

        assertThat(upgrader.upgrade(AiPromptKind.REVIEWER, "REVIEWER", ReviewPromptDefaults.content())).isZero();
        verify(versions, never()).save(any());
    }

    @Test
    void theShippedSharedBlockAsksForBothHints() {
        PromptContent shared = codec.read(PromptTestRegistry.find(AiPromptKind.SHARED, "COMMON_GENERATOR")
                .orElseThrow().getContent());
        assertThat(PromptTextAssembler.assemble(shared)).contains("answerHintVi", "answerHintEn");
        assertThat(shared.sections()).extracting(PromptContent.Section::id).endsWith("answer-hints", "answer-hint-style");
    }
}
