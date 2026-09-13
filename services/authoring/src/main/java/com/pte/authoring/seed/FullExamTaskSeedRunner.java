package com.pte.authoring.seed;

import com.pte.authoring.domain.BlueprintItem;
import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.enums.BlueprintStatus;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.QuestionStatus;
import com.pte.authoring.domain.enums.Visibility;
import com.pte.authoring.repository.ExamBlueprintRepository;
import com.pte.authoring.repository.QuestionRepository;
import com.pte.authoring.service.QuestionValidationHelper;
import com.pte.authoring.service.SnapshotPublishService;
import com.pte.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Dev/local-only seed for one structurally valid sample of every PTE task type.
 * The fixture uses generic content and deterministic placeholder media IDs; it
 * is intentionally separate from production data and only runs under the
 * {@code seed-full-exam} profile.
 */
@Component
@Profile("seed-full-exam")
public class FullExamTaskSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FullExamTaskSeedRunner.class);
    private static final String FIXTURE_RESOURCE = "seed/full-exam-task-fixtures.json";
    private static final String SEED_BLUEPRINT_NAME = "Full PTE Academic Task Types — Dev Seed";

    private final QuestionRepository questionRepository;
    private final ExamBlueprintRepository blueprintRepository;
    private final SnapshotPublishService snapshotPublishService;
    private final QuestionValidationHelper validationHelper;
    private final JsonMapper jsonMapper;

    public FullExamTaskSeedRunner(QuestionRepository questionRepository,
                                  ExamBlueprintRepository blueprintRepository,
                                  SnapshotPublishService snapshotPublishService,
                                  QuestionValidationHelper validationHelper,
                                  JsonMapper jsonMapper) {
        this.questionRepository = questionRepository;
        this.blueprintRepository = blueprintRepository;
        this.snapshotPublishService = snapshotPublishService;
        this.validationHelper = validationHelper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(String... args) {
        if (blueprintRepository.findAll().stream()
                .anyMatch(blueprint -> SEED_BLUEPRINT_NAME.equals(blueprint.getName()))) {
            log.info("Full exam task type dev seed already present — skipping ({}).", SEED_BLUEPRINT_NAME);
            return;
        }

        List<SeedQuestion> fixture = loadFixture();
        validateFixtureCoverage(fixture);

        List<Question> savedQuestions = fixture.stream()
                .map(this::toQuestion)
                .map(question -> {
                    validationHelper.validate(question);
                    return questionRepository.save(question);
                })
                .toList();

        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(SEED_BLUEPRINT_NAME);
        blueprint.setTenantId(null);
        blueprint.setStatus(BlueprintStatus.DRAFT);
        for (int index = 0; index < savedQuestions.size(); index++) {
            blueprint.addItem(itemFor(savedQuestions.get(index), index));
        }

        ExamBlueprint savedBlueprint = blueprintRepository.save(blueprint);
        CurrentUser systemSeedCaller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_AUTHOR"));
        snapshotPublishService.publish(savedBlueprint.getPublicId(), systemSeedCaller);
        log.info("Seeded and published all {} PTE task type fixtures (blueprint {}).",
                savedQuestions.size(), savedBlueprint.getPublicId());
    }

    private List<SeedQuestion> loadFixture() {
        try (InputStream input = new ClassPathResource(FIXTURE_RESOURCE).getInputStream()) {
            return jsonMapper.readValue(input, new TypeReference<List<SeedQuestion>>() {
            });
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Failed to load full exam seed fixture from " + FIXTURE_RESOURCE, ex);
        }
    }

    private void validateFixtureCoverage(List<SeedQuestion> fixture) {
        if (fixture == null || fixture.size() != PteTaskType.values().length) {
            throw new IllegalStateException("Full exam seed fixture must contain exactly "
                    + PteTaskType.values().length + " task types");
        }

        EnumSet<PteTaskType> actualTypes = EnumSet.noneOf(PteTaskType.class);
        for (SeedQuestion seed : fixture) {
            PteTaskType type = parseTaskType(seed.taskType());
            if (!actualTypes.add(type)) {
                throw new IllegalStateException("Duplicate task type in full exam seed fixture: " + type);
            }
        }
        if (!actualTypes.equals(EnumSet.allOf(PteTaskType.class))) {
            EnumSet<PteTaskType> missing = EnumSet.allOf(PteTaskType.class);
            missing.removeAll(actualTypes);
            throw new IllegalStateException("Missing task types in full exam seed fixture: " + missing);
        }
    }

    private Question toQuestion(SeedQuestion seed) {
        PteTaskType taskType = parseTaskType(seed.taskType());
        Question question = new Question();
        question.setPteTaskType(taskType);
        question.setVisibility(Visibility.SHARED);
        question.setTenantId(null);
        question.setStatus(QuestionStatus.PUBLISHED);
        question.setTitle(seed.title());
        question.setPromptText(seed.promptText());
        question.setAudioPromptRef(seed.audioPromptRef());
        question.setImagePromptRef(seed.imagePromptRef());
        question.setReferenceAnswerText(seed.referenceAnswerText());
        question.setCorrectAnswerText(seed.correctAnswerText());
        question.setMinWordCount(seed.minWordCount());
        question.setMaxWordCount(seed.maxWordCount());
        if (seed.options() != null) {
            seed.options().forEach(option -> question.addOption(toOption(option)));
        }
        return question;
    }

    private QuestionOption toOption(SeedOption source) {
        QuestionOption option = new QuestionOption();
        option.setText(source.text());
        option.setCorrect(source.correct());
        option.setOrderIndex(source.orderIndex());
        option.setBlankIndex(source.blankIndex());
        option.setCorrectGapIndex(source.correctGapIndex());
        return option;
    }

    private BlueprintItem itemFor(Question question, int orderIndex) {
        BlueprintItem item = new BlueprintItem();
        item.setQuestionPublicId(question.getPublicId());
        item.setSection(question.getPteTaskType().getSection());
        item.setOrderIndex(orderIndex);
        return item;
    }

    private PteTaskType parseTaskType(String value) {
        try {
            return PteTaskType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException("Unknown task type in full exam seed fixture: " + value, ex);
        }
    }

    public record SeedQuestion(
            String taskType,
            String title,
            String promptText,
            UUID audioPromptRef,
            UUID imagePromptRef,
            String referenceAnswerText,
            String correctAnswerText,
            Integer minWordCount,
            Integer maxWordCount,
            List<SeedOption> options) {
    }

    public record SeedOption(
            String text,
            boolean correct,
            int orderIndex,
            Integer blankIndex,
            Integer correctGapIndex) {
    }
}
