package com.pte.itembank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.dto.response.TaskTypeStimulusResponse;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.media.MediaService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTypeStimulusServiceTest {

    @Mock
    private QuestionTypeRepository questionTypeRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private MediaService mediaService;

    private TaskTypeStimulusService service;

    @BeforeEach
    void setUp() {
        service = new TaskTypeStimulusService(questionTypeRepository, questionRepository, mediaService);
    }

    private static QuestionType type(String key, boolean beforeTimer, int estimate) {
        QuestionType type = new QuestionType();
        type.setTaskTypeKey(key);
        type.setStimulusBeforeTimer(beforeTimer);
        type.setStimulusEstimateSeconds(estimate);
        return type;
    }

    @Test
    void reportsOnlyTheTaskTypesThatPlayAStimulusBeforeTheTimer() {
        when(questionTypeRepository.findAllByTaskTypeKeyIn(Set.of("RE_TELL_LECTURE", "READ_ALOUD")))
                .thenReturn(List.of(type("RE_TELL_LECTURE", true, 85), type("READ_ALOUD", false, 0)));

        Map<String, TaskTypeStimulusResponse> result =
                service.stimulusBeforeTimer(Set.of("RE_TELL_LECTURE", "READ_ALOUD"));

        assertThat(result).containsOnlyKeys("RE_TELL_LECTURE");
    }

    @Test
    void usesTheMedianMeasuredLengthWhenQuestionsHaveOne() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(questionTypeRepository.findAllByTaskTypeKeyIn(Set.of("RE_TELL_LECTURE")))
                .thenReturn(List.of(type("RE_TELL_LECTURE", true, 85)));
        when(questionRepository.findExamAudioPromptRefsByTaskTypeKeys(Set.of("RE_TELL_LECTURE")))
                .thenReturn(List.of(new Object[] {"RE_TELL_LECTURE", first},
                        new Object[] {"RE_TELL_LECTURE", second}, new Object[] {"RE_TELL_LECTURE", third}));
        when(mediaService.durationSeconds(List.of(first, second, third)))
                .thenReturn(Map.of(first, 40, second, 70, third, 100));

        TaskTypeStimulusResponse stimulus = service.stimulusBeforeTimer(Set.of("RE_TELL_LECTURE"))
                .get("RE_TELL_LECTURE");

        assertThat(stimulus.seconds()).isEqualTo(70);
        assertThat(stimulus.measuredQuestions()).isEqualTo(3);
    }

    @Test
    void fallsBackToTheConfiguredEstimateWhenNoQuestionHasAMeasuredLength() {
        when(questionTypeRepository.findAllByTaskTypeKeyIn(Set.of("REPEAT_SENTENCE")))
                .thenReturn(List.of(type("REPEAT_SENTENCE", true, 6)));
        when(questionRepository.findExamAudioPromptRefsByTaskTypeKeys(Set.of("REPEAT_SENTENCE")))
                .thenReturn(List.of());
        when(mediaService.durationSeconds(List.of())).thenReturn(Map.of());

        TaskTypeStimulusResponse stimulus = service.stimulusBeforeTimer(Set.of("REPEAT_SENTENCE"))
                .get("REPEAT_SENTENCE");

        assertThat(stimulus.seconds()).isEqualTo(6);
        assertThat(stimulus.measuredQuestions()).isZero();
    }
}
