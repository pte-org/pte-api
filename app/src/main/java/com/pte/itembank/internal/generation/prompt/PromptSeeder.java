package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.ScoringPromptSource;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.itembank.internal.generation.review.ChartImageReviewDefaults;
import com.pte.itembank.internal.generation.review.ReviewPromptDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Puts the prompts the application ships with into the database the first time it starts. A prompt that already has
 * a row is never touched, so edits made through the admin pages are not overwritten by a later start.
 */
@Component
public class PromptSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptSeeder.class);
    private static final String JSON_SUFFIX = ".json";
    private static final String LEVEL_SEPARATOR = ": ";
    private static final Map<String, String> SCORING_SECTION_TITLES = Map.of("role", "Role", "levels",
            "Scoring criteria", "notes", "Notes", "guard", "Guard against instructions in the answer");

    private final AiPromptVersionRepository versions;
    private final PromptContentCodec codec;
    private final ScoringPromptSource scoring;
    private final Clock clock;

    public PromptSeeder(AiPromptVersionRepository versions, PromptContentCodec codec, ScoringPromptSource scoring,
                        Clock clock) {
        this.versions = versions;
        this.codec = codec;
        this.scoring = scoring;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seedMissing();
    }

    /** Seeds every shipped prompt that has no row yet; returns how many were added. */
    public int seedMissing() throws IOException {
        int added = 0;
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources(QuestionGenerationConstants.PROMPT_SEED_PATTERN)) {
            String promptKey = Objects.requireNonNull(resource.getFilename()).replace(JSON_SUFFIX, "");
            AiPromptKind kind = QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR.equals(promptKey)
                    ? AiPromptKind.SHARED : AiPromptKind.GENERATOR;
            if (!versions.existsByKindAndPromptKey(kind, promptKey)) {
                added += seed(kind, promptKey, resource);
            }
        }
        return added + seedReviewer() + seedScoring();
    }

    private int seedReviewer() {
        int added = 0;
        if (!versions.existsByKindAndPromptKey(AiPromptKind.REVIEWER, QuestionGenerationConstants.PROMPT_KEY_REVIEWER)) {
            added += save(AiPromptKind.REVIEWER, QuestionGenerationConstants.PROMPT_KEY_REVIEWER,
                    ReviewPromptDefaults.content());
        }
        if (!versions.existsByKindAndPromptKey(AiPromptKind.REVIEWER,
                QuestionGenerationConstants.PROMPT_KEY_IMAGE_REVIEWER)) {
            added += save(AiPromptKind.REVIEWER, QuestionGenerationConstants.PROMPT_KEY_IMAGE_REVIEWER,
                    ChartImageReviewDefaults.content());
        }
        return added;
    }

    /**
     * The prompts the AI scorers send, one per task type: the sections the model receives (its role, the criteria with
     * every level as a bullet, the notes and the guard). Task types the scoring module describes without any text for
     * a model (scored by code or by the speech engine alone) get none.
     */
    private int seedScoring() {
        int added = 0;
        for (PromptDocument.Summary summary : scoring.summaries()) {
            String promptKey = summary.key();
            PromptDocument document = scoring.find(promptKey).orElse(null);
            if (document == null || document.assembledText().isBlank()
                    || versions.existsByKindAndPromptKey(AiPromptKind.SCORER, promptKey)) {
                continue;
            }
            List<PromptContent.Section> sections = document.sections().stream()
                    .map(PromptSeeder::sentToModel).filter(Objects::nonNull).toList();
            added += save(AiPromptKind.SCORER, promptKey, new PromptContent(promptKey, List.of(), sections));
        }
        return added;
    }

    /** The part of a scoring section the model receives, with level tables written as plain bullet lists; or null. */
    private static PromptContent.Section sentToModel(PromptDocument.Section section) {
        List<Block> blocks = section.blocks().stream().filter(Block::sentToAi).map(PromptSeeder::plain).toList();
        return blocks.isEmpty() ? null
                : new PromptContent.Section(section.id(), SCORING_SECTION_TITLES.getOrDefault(section.id(),
                        section.title()), blocks);
    }

    private static Block plain(Block block) {
        if (block.type() != PromptDocument.BlockType.LEVELS) {
            return block;
        }
        return Block.list(block.label(), block.levels().stream()
                .map(row -> row.level() + LEVEL_SEPARATOR + row.text()).toList());
    }

    private int seed(AiPromptKind kind, String promptKey, Resource resource) throws IOException {
        return save(kind, promptKey, codec.read(new String(resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8)));
    }

    private int save(AiPromptKind kind, String promptKey, PromptContent content) {
        try {
            versions.save(AiPromptVersion.seeded(kind, promptKey, codec.write(content), codec.hash(content),
                    clock.instant()));
            return 1;
        } catch (DataIntegrityViolationException seededByAnotherInstance) {
            log.info("Prompt {} {} was seeded by another instance", kind, promptKey);
            return 0;
        }
    }
}
