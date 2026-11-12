package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.PromptTexts;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Reads the chatbot's and the AI scorers' prompts in use, and first stores the chatbot's for the chat module. */
@Service
public class PromptTextsService implements PromptTexts {

    private static final String PARAGRAPH_BREAK = "\\n\\s*\\n";
    private static final String SLUG_NOISE = "[^a-z0-9]+";

    private final AiPromptVersionRepository versions;
    private final PromptContentCodec codec;
    private final Clock clock;

    public PromptTextsService(AiPromptVersionRepository versions, PromptContentCodec codec, Clock clock) {
        this.versions = versions;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public Optional<String> activeText(String category, String key) {
        return active(category, key).map(ActivePrompt::text);
    }

    @Override
    public Optional<ActivePrompt> active(String category, String key) {
        return kindOf(category).flatMap(kind -> versions.findByKindAndPromptKeyAndStatus(kind, key,
                        AiPromptStatus.ACTIVE))
                .map(version -> new ActivePrompt(PromptTextAssembler.assembleWithoutTitle(
                        codec.read(version.getContent())), version.getVersionNo(), version.getPublicId()));
    }

    @Override
    public void seedIfMissing(String category, String key, String title, List<Part> parts) {
        AiPromptKind kind = kindOf(category).orElseThrow(() -> new IllegalArgumentException(category));
        if (versions.existsByKindAndPromptKey(kind, key)) {
            return;
        }
        List<Block> intro = new ArrayList<>();
        List<PromptContent.Section> sections = new ArrayList<>();
        for (Part part : parts) {
            List<Block> blocks = Arrays.stream(part.text().strip().split(PARAGRAPH_BREAK)).map(String::strip)
                    .filter(paragraph -> !paragraph.isEmpty()).map(paragraph -> Block.text("", paragraph)).toList();
            if (part.title() == null || part.title().isBlank()) {
                intro.addAll(blocks);
            } else {
                sections.add(new PromptContent.Section(slug(part.title()), part.title().strip(), blocks));
            }
        }
        PromptContent content = new PromptContent(title, intro, sections);
        try {
            versions.save(AiPromptVersion.seeded(kind, key, codec.write(content), codec.hash(content),
                    clock.instant()));
        } catch (DataIntegrityViolationException seededByAnotherInstance) {
            // Another instance stored the same prompt first, which is just as good.
        }
    }

    private static Optional<AiPromptKind> kindOf(String category) {
        if (CHATBOT_CATEGORY.equals(category)) {
            return Optional.of(AiPromptKind.CHATBOT);
        }
        return SCORING_CATEGORY.equals(category) ? Optional.of(AiPromptKind.SCORER) : Optional.empty();
    }

    private static String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll(SLUG_NOISE, "-").replaceAll("^-|-$", "");
    }
}
