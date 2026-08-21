package com.pte.authoring.seed;

import com.pte.authoring.domain.BlueprintItem;
import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.enums.BlueprintStatus;
import com.pte.authoring.domain.enums.PteSection;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.QuestionStatus;
import com.pte.authoring.domain.enums.Visibility;
import com.pte.authoring.repository.ExamBlueprintRepository;
import com.pte.authoring.repository.QuestionRepository;
import com.pte.authoring.service.SnapshotPublishService;
import com.pte.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Dev/local-only seed data for the 4 new PTE Reading task types
 * (ninh-pte-reading-task-types Phase 8) — inserts {@link Question}/{@link
 * QuestionOption} rows through the normal repository layer (not a
 * hand-crafted {@code optionsJson} blob) and publishes them through the real
 * {@link SnapshotPublishService#publish} path, so a local `exam-delivery`
 * serves genuine, structurally-correct content — critically including a
 * populated {@code blankGroups} example for {@code FILL_BLANKS_READING_WRITING}.
 * Active only under the {@code seed-reading-tasks} profile — never runs by
 * default, and idempotent (skips if the seed blueprint already exists).
 */
@Component
@Profile("seed-reading-tasks")
public class ReadingTaskSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReadingTaskSeedRunner.class);
    private static final String SEED_BLUEPRINT_NAME = "Reading Task Types — Dev Seed";

    private final QuestionRepository questionRepository;
    private final ExamBlueprintRepository blueprintRepository;
    private final SnapshotPublishService snapshotPublishService;

    public ReadingTaskSeedRunner(QuestionRepository questionRepository, ExamBlueprintRepository blueprintRepository,
                                  SnapshotPublishService snapshotPublishService) {
        this.questionRepository = questionRepository;
        this.blueprintRepository = blueprintRepository;
        this.snapshotPublishService = snapshotPublishService;
    }

    @Override
    public void run(String... args) {
        boolean alreadySeeded = blueprintRepository.findAll().stream()
                .anyMatch(blueprint -> SEED_BLUEPRINT_NAME.equals(blueprint.getName()));
        if (alreadySeeded) {
            log.info("Reading task type dev seed already present — skipping ({}).", SEED_BLUEPRINT_NAME);
            return;
        }

        Question mcMultiple = questionRepository.save(buildMcReadingMultiple());
        Question reorder = questionRepository.save(buildReOrderParagraphs());
        Question fillBlanksDragDrop = questionRepository.save(buildFillBlanksReading());
        Question fillBlanksDropdown = questionRepository.save(buildFillBlanksReadingWriting());

        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setName(SEED_BLUEPRINT_NAME);
        blueprint.setTenantId(null);
        blueprint.setStatus(BlueprintStatus.DRAFT);
        blueprint.addItem(itemFor(mcMultiple, 0));
        blueprint.addItem(itemFor(reorder, 1));
        blueprint.addItem(itemFor(fillBlanksDragDrop, 2));
        blueprint.addItem(itemFor(fillBlanksDropdown, 3));
        ExamBlueprint savedBlueprint = blueprintRepository.save(blueprint);

        CurrentUser systemSeedCaller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_AUTHOR"));
        snapshotPublishService.publish(savedBlueprint.getPublicId(), systemSeedCaller);
        log.info("Seeded and published reading task type dev fixtures (blueprint {}).", savedBlueprint.getPublicId());
    }

    private BlueprintItem itemFor(Question question, int orderIndex) {
        BlueprintItem item = new BlueprintItem();
        item.setQuestionPublicId(question.getPublicId());
        item.setSection(PteSection.READING);
        item.setOrderIndex(orderIndex);
        return item;
    }

    private Question buildMcReadingMultiple() {
        Question question = newSharedQuestion(PteTaskType.MC_READING_MULTIPLE, "Light pollution — multiple answers");
        question.setPromptText(
                "At the end of the day, the sun goes down but our cities light up. Billboards, office buildings, "
                        + "streetlights, cars, and many other things illuminate the sky. This artificial lighting can "
                        + "benefit society in many ways, but this night light also has some downsides. According to "
                        + "the text, problems from light pollution include:");
        question.addOption(option("health problems in humans", 0, true, null, null));
        question.addOption(option("people sleeping longer", 1, false, null, null));
        question.addOption(option("trees dying", 2, true, null, null));
        question.addOption(option("people not getting enough energy", 3, false, null, null));
        question.addOption(option("birds getting lost", 4, true, null, null));
        return question;
    }

    private Question buildReOrderParagraphs() {
        Question question = newSharedQuestion(PteTaskType.RE_ORDER_PARAGRAPHS, "Shackleton's Endurance — re-order");
        // orderIndex is each paragraph's correct final position (0 = first);
        // SnapshotPublishService.deliveryOrder rotates the served array so
        // the student sees them out of order, per Phase 8's discovered fix.
        question.addOption(option(
                "First, the researchers gathered samples from three different sites along the coast.", 0, false, null, null));
        question.addOption(option(
                "The samples were then analyzed over several months back at the laboratory.", 1, false, null, null));
        question.addOption(option(
                "Unexpected patterns emerged from the analysis that the team had not predicted.", 2, false, null, null));
        question.addOption(option(
                "Finally, the team published their findings in a major scientific journal.", 3, false, null, null));
        return question;
    }

    private Question buildFillBlanksReading() {
        Question question = newSharedQuestion(PteTaskType.FILL_BLANKS_READING, "Anti-fairy tales — drag & drop");
        question.setPromptText(
                "An anti-fairy tale which, unlike an ordinary one, has a {{0}}, rather than a happy ending, with "
                        + "the main characters suffering loss by the end of the story. While fairy tales paint a "
                        + "magical, perfect world, anti-fairy tales paint a dark world full of {{1}} and cruelty.");
        // Two correct words (one per gap) plus distractors — a word bank
        // larger than the gap count, per Design Constraints.
        question.addOption(option("tragic", 0, true, null, 0));
        question.addOption(option("boring", 1, false, null, null));
        question.addOption(option("happiness", 2, false, null, null));
        question.addOption(option("twists", 3, false, null, null));
        question.addOption(option("nastiness", 4, true, null, 1));
        question.addOption(option("events", 5, false, null, null));
        return question;
    }

    private Question buildFillBlanksReadingWriting() {
        Question question = newSharedQuestion(PteTaskType.FILL_BLANKS_READING_WRITING, "Light pollution — dropdown fill-blanks");
        question.setPromptText(
                "Lighting uses about 25% of the world's electricity, but it is not always used {{0}}. Some lights "
                        + "are kept on even when there is nobody {{1}} them.");
        // Blank 0's own distinct option group.
        question.addOption(option("efficiently", 0, true, 0, null));
        question.addOption(option("quickly", 1, false, 0, null));
        question.addOption(option("rarely", 2, false, 0, null));
        // Blank 1's own distinct option group — different words entirely.
        question.addOption(option("using", 0, true, 1, null));
        question.addOption(option("needing", 1, false, 1, null));
        question.addOption(option("building", 2, false, 1, null));
        return question;
    }

    private Question newSharedQuestion(PteTaskType taskType, String title) {
        Question question = new Question();
        question.setPteTaskType(taskType);
        question.setVisibility(Visibility.SHARED);
        question.setTenantId(null);
        question.setStatus(QuestionStatus.PUBLISHED);
        question.setTitle(title);
        return question;
    }

    private QuestionOption option(String text, int orderIndex, boolean correct, Integer blankIndex, Integer correctGapIndex) {
        QuestionOption option = new QuestionOption();
        option.setText(text);
        option.setOrderIndex(orderIndex);
        option.setCorrect(correct);
        option.setBlankIndex(blankIndex);
        option.setCorrectGapIndex(correctGapIndex);
        return option;
    }
}
