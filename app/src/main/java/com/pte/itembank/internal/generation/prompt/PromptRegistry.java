package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The prompts the generator uses: for each task type and for the block they share, the version that is ACTIVE in the
 * database. Text is assembled from the stored JSON on every read, so a newly activated version is used by the next
 * item without a restart. The prompts carry the placeholders {cefr}, {topicHint}, {avoidTopics} and {lengthRules}.
 */
@Component
public class PromptRegistry {

    private final AiPromptVersionRepository versions;
    private final PromptContentCodec codec;

    public PromptRegistry(AiPromptVersionRepository versions, PromptContentCodec codec) {
        this.versions = versions;
        this.codec = codec;
    }

    /** The instruction prompt of a task type, or null when it has none. Placeholders are filled in by the generator. */
    public String getPrompt(String taskTypeKey) {
        return generatorContent(taskTypeKey).map(PromptTextAssembler::assemble).orElse(null);
    }

    /** The instruction shared by all task types; null when it has not been stored. */
    public String getCommonPrompt() {
        return commonContent().map(PromptTextAssembler::assemble).orElse(null);
    }

    public Optional<PromptContent> generatorContent(String taskTypeKey) {
        return active(AiPromptKind.GENERATOR, taskTypeKey.toUpperCase(Locale.ROOT)).map(this::contentOf);
    }

    /** The reviewer's prompt in use, as the text the model receives before the task type and traits are filled in. */
    public Optional<String> reviewerPrompt() {
        return active(AiPromptKind.REVIEWER, QuestionGenerationConstants.PROMPT_KEY_REVIEWER).map(this::contentOf)
                .map(PromptTextAssembler::assembleWithoutTitle);
    }

    /** The image reviewer's prompt in use, as the text the model receives. */
    public Optional<String> imageReviewerPrompt() {
        return active(AiPromptKind.REVIEWER, QuestionGenerationConstants.PROMPT_KEY_IMAGE_REVIEWER)
                .map(this::contentOf).map(PromptTextAssembler::assembleWithoutTitle);
    }

    public Optional<PromptContent> commonContent() {
        return active(AiPromptKind.SHARED, QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR)
                .map(this::contentOf);
    }

    /** Every task-type prompt's content, by task type, in a stable order. */
    public Map<String, PromptContent> allContents() {
        Map<String, PromptContent> contents = new TreeMap<>();
        versions.findByKindAndStatus(AiPromptKind.GENERATOR, AiPromptStatus.ACTIVE)
                .forEach(version -> contents.put(version.getPromptKey(), contentOf(version)));
        return contents;
    }

    /** Every task-type prompt as the text the generator sends, by task type, in a stable order. */
    public Map<String, String> all() {
        Map<String, String> texts = new TreeMap<>();
        allContents().forEach((taskType, content) -> texts.put(taskType, PromptTextAssembler.assemble(content)));
        return texts;
    }

    /** The titles of the prompts in use of one kind, by key, in a stable order. */
    public Map<String, String> activeTitles(AiPromptKind kind) {
        Map<String, String> titles = new TreeMap<>();
        versions.findByKindAndStatus(kind, AiPromptStatus.ACTIVE)
                .forEach(version -> titles.put(version.getPromptKey(), contentOf(version).title()));
        return titles;
    }

    /** The highest version number among the live generator prompts: it rises whenever one of them is replaced. */
    public int getPromptVersion() {
        return versions.findByKindAndStatus(AiPromptKind.GENERATOR, AiPromptStatus.ACTIVE).stream()
                .map(AiPromptVersion::getVersionNo)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    private Optional<AiPromptVersion> active(AiPromptKind kind, String promptKey) {
        return versions.findByKindAndPromptKeyAndStatus(kind, promptKey, AiPromptStatus.ACTIVE);
    }

    private PromptContent contentOf(AiPromptVersion version) {
        return codec.read(version.getContent());
    }
}
