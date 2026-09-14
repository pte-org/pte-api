package com.pte.authoring.seed;

import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.enums.PteSection;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.service.QuestionValidationHelper;
import com.pte.authoring.service.SnapshotPublishService;
import com.pte.authoring.repository.ExamBlueprintRepository;
import com.pte.authoring.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FullExamTaskSeedRunnerTest {

    private static final String SEED_BLUEPRINT_NAME = "Full PTE Academic Task Types — Dev Seed";

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ExamBlueprintRepository blueprintRepository;

    @Mock
    private SnapshotPublishService snapshotPublishService;

    private FullExamTaskSeedRunner runner;

    @BeforeEach
    void setUp() {
        when(blueprintRepository.findAll()).thenReturn(List.of());
        runner = new FullExamTaskSeedRunner(
                questionRepository,
                blueprintRepository,
                snapshotPublishService,
                new QuestionValidationHelper(),
                new JsonMapper());
    }

    @Test
    void freshRun_seedsExactlyOneValidQuestionPerTaskType_andPublishesOrderedBlueprint() {
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setPublicId(UUID.randomUUID());
            return question;
        });
        when(blueprintRepository.save(any(ExamBlueprint.class))).thenAnswer(invocation -> {
            ExamBlueprint blueprint = invocation.getArgument(0);
            blueprint.setPublicId(UUID.randomUUID());
            return blueprint;
        });

        runner.run();

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, org.mockito.Mockito.times(PteTaskType.values().length))
                .save(questionCaptor.capture());

        List<Question> questions = questionCaptor.getAllValues();
        assertThat(questions).hasSize(PteTaskType.values().length);
        assertThat(questions).extracting(Question::getPteTaskType)
                .containsExactly(
                        PteTaskType.PERSONAL_INTRODUCTION,
                        PteTaskType.READ_ALOUD,
                        PteTaskType.REPEAT_SENTENCE,
                        PteTaskType.DESCRIBE_IMAGE,
                        PteTaskType.RE_TELL_LECTURE,
                        PteTaskType.ANSWER_SHORT_QUESTION,
                        PteTaskType.SUMMARIZE_GROUP_DISCUSSION,
                        PteTaskType.RESPOND_TO_A_SITUATION,
                        PteTaskType.SUMMARIZE_WRITTEN_TEXT,
                        PteTaskType.WRITE_ESSAY,
                        PteTaskType.MC_READING_SINGLE,
                        PteTaskType.MC_READING_MULTIPLE,
                        PteTaskType.RE_ORDER_PARAGRAPHS,
                        PteTaskType.FILL_BLANKS_READING,
                        PteTaskType.FILL_BLANKS_READING_WRITING,
                        PteTaskType.SUMMARIZE_SPOKEN_TEXT,
                        PteTaskType.MC_LISTENING_SINGLE,
                        PteTaskType.MC_LISTENING_MULTIPLE,
                        PteTaskType.FILL_BLANKS_LISTENING,
                        PteTaskType.HIGHLIGHT_CORRECT_SUMMARY,
                        PteTaskType.SELECT_MISSING_WORD,
                        PteTaskType.HIGHLIGHT_INCORRECT_WORDS,
                        PteTaskType.WRITE_FROM_DICTATION);
        assertThat(questions).extracting(Question::getTitle)
                .doesNotHaveDuplicates();

        for (Question question : questions) {
            PteTaskType type = question.getPteTaskType();
            assertThat(question.getVisibility().name()).isEqualTo("SHARED");
            assertThat(question.getStatus().name()).isEqualTo("PUBLISHED");
            assertThat(question.getTitle()).isNotBlank();
            if (type.requiresAudioPrompt()) {
                assertThat(question.getAudioPromptRef()).as(type.name()).isNotNull();
            }
            if (type.requiresImagePrompt()) {
                assertThat(question.getImagePromptRef()).as(type.name()).isNotNull();
            }
            if (type.requiresPromptText()) {
                assertThat(question.getPromptText()).as(type.name()).isNotBlank();
            }
            if (type.requiresWordCount()) {
                assertThat(question.getMinWordCount()).as(type.name()).isPositive();
                assertThat(question.getMaxWordCount()).as(type.name())
                        .isGreaterThanOrEqualTo(question.getMinWordCount());
            }
        }

        ArgumentCaptor<ExamBlueprint> blueprintCaptor = ArgumentCaptor.forClass(ExamBlueprint.class);
        verify(blueprintRepository).save(blueprintCaptor.capture());
        ExamBlueprint blueprint = blueprintCaptor.getValue();
        assertThat(blueprint.getName()).isEqualTo(SEED_BLUEPRINT_NAME);
        assertThat(blueprint.getItems()).hasSize(PteTaskType.values().length);
        assertThat(blueprint.getItems()).extracting(item -> item.getOrderIndex())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, PteTaskType.values().length)
                        .boxed().toList());
        assertThat(blueprint.getItems()).extracting(item -> item.getSection())
                .containsExactly(PteSection.SPEAKING, PteSection.SPEAKING, PteSection.SPEAKING,
                        PteSection.SPEAKING, PteSection.SPEAKING, PteSection.SPEAKING,
                        PteSection.SPEAKING, PteSection.SPEAKING, PteSection.WRITING,
                        PteSection.WRITING, PteSection.READING, PteSection.READING,
                        PteSection.READING, PteSection.READING, PteSection.READING,
                        PteSection.LISTENING, PteSection.LISTENING, PteSection.LISTENING,
                        PteSection.LISTENING, PteSection.LISTENING, PteSection.LISTENING,
                        PteSection.LISTENING, PteSection.LISTENING);
        verify(snapshotPublishService).publish(eq(blueprint.getPublicId()), any());
    }

    @Test
    void existingSentinelBlueprint_skipsAllWrites() {
        ExamBlueprint existing = new ExamBlueprint();
        existing.setName(SEED_BLUEPRINT_NAME);
        when(blueprintRepository.findAll()).thenReturn(List.of(existing));

        runner.run();

        verify(questionRepository, never()).save(any(Question.class));
        verify(blueprintRepository, never()).save(any(ExamBlueprint.class));
        verify(snapshotPublishService, never()).publish(any(), any());
    }
}
