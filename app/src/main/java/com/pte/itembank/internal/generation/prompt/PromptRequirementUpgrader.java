package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.prompt.version.PromptRequirements;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Brings a live prompt up to the instructions the code now depends on. When the live version lacks a required
 * phrase, the shipped section carrying it is appended as a new live version and the old one is retired, so the
 * prompt shown in the admin pages is the prompt the model receives. Edits made through the pages are kept.
 */
@Component
public class PromptRequirementUpgrader {

    private static final Logger log = LoggerFactory.getLogger(PromptRequirementUpgrader.class);

    private final AiPromptVersionRepository versions;
    private final PromptContentCodec codec;
    private final Clock clock;

    public PromptRequirementUpgrader(AiPromptVersionRepository versions, PromptContentCodec codec, Clock clock) {
        this.versions = versions;
        this.codec = codec;
        this.clock = clock;
    }

    /** Upgrades the live version of one prompt from its shipped content; returns 1 when a version was added. */
    @Transactional
    public int upgrade(AiPromptKind kind, String promptKey, PromptContent shipped) {
        AiPromptVersion live = versions.findByKindAndPromptKeyAndStatus(kind, promptKey, AiPromptStatus.ACTIVE)
                .orElse(null);
        if (live == null) {
            return 0;
        }
        PromptContent current = codec.read(live.getContent());
        List<String> missing = PromptRequirements.missingPhrases(kind, promptKey,
                PromptTextAssembler.assemble(current));
        if (missing.isEmpty()) {
            return 0;
        }
        PromptContent upgraded = withSectionsFor(current, shipped);
        if (!PromptRequirements.missingPhrases(kind, promptKey, PromptTextAssembler.assemble(upgraded)).isEmpty()) {
            log.warn("Prompt {} {} lacks {} and the shipped prompt has no section to add", kind, promptKey, missing);
            return 0;
        }
        replaceLive(kind, promptKey, upgraded);
        return 1;
    }

    /** Removes output fields no code reads from every live writer prompt; returns how many versions were added. */
    @Transactional
    public int dropObsoleteFields() {
        int added = 0;
        for (AiPromptVersion live : versions.findByKindAndStatus(AiPromptKind.GENERATOR, AiPromptStatus.ACTIVE)) {
            PromptContent current = codec.read(live.getContent());
            PromptContent cleaned = PromptObsoleteFields.removedFrom(live.getPromptKey(), current);
            if (!cleaned.equals(current)) {
                replaceLive(AiPromptKind.GENERATOR, live.getPromptKey(), cleaned);
                added++;
            }
        }
        return added;
    }

    /** A new live version with {@code content}; the old one is retired and stays in the history. */
    private void replaceLive(AiPromptKind kind, String promptKey, PromptContent content) {
        Instant now = clock.instant();
        int versionNo = versions.highestVersionNo(kind.name(), promptKey) + 1;
        versions.retireActive(kind, promptKey, now);
        AiPromptVersion next = AiPromptVersion.draft(kind, promptKey, versionNo, codec.write(content),
                codec.hash(content), null);
        next.activate(null, now);
        versions.save(next);
        log.info("Prompt {} {} upgraded to version {} by the system", kind, promptKey, versionNo);
    }

    /** The current content plus every shipped section it does not have yet (the required one and its companions). */
    private static PromptContent withSectionsFor(PromptContent current, PromptContent shipped) {
        Set<String> ids = current.sections().stream().map(PromptContent.Section::id).collect(Collectors.toSet());
        List<PromptContent.Section> sections = new ArrayList<>(current.sections());
        shipped.sections().stream().filter(section -> !ids.contains(section.id())).forEach(sections::add);
        return new PromptContent(current.title(), current.intro(), sections);
    }
}
