package com.pte.itembank;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.dto.response.TaskTypeStimulusResponse;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.StimulusLength;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reports the audio stimulus that task types play before their answer timer, for planning exam duration. */
@Service
public class TaskTypeStimulusService {

    private final QuestionTypeRepository questionTypeRepository;
    private final QuestionRepository questionRepository;
    private final MediaService mediaService;

    public TaskTypeStimulusService(QuestionTypeRepository questionTypeRepository,
            QuestionRepository questionRepository, MediaService mediaService) {
        this.questionTypeRepository = questionTypeRepository;
        this.questionRepository = questionRepository;
        this.mediaService = mediaService;
    }

    /** Stimulus of the given task types that play one before the timer; other task types are left out. */
    @Transactional(readOnly = true)
    public Map<String, TaskTypeStimulusResponse> stimulusBeforeTimer(Collection<String> taskTypeKeys) {
        if (taskTypeKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, QuestionType> playing = questionTypeRepository.findAllByTaskTypeKeyIn(taskTypeKeys).stream()
                .filter(QuestionType::isStimulusBeforeTimer)
                .collect(Collectors.toMap(QuestionType::getTaskTypeKey, type -> type));
        if (playing.isEmpty()) {
            return Map.of();
        }
        Map<String, List<UUID>> audioByType = new HashMap<>();
        List<UUID> allAudio = new ArrayList<>();
        for (Object[] row : questionRepository.findExamAudioPromptRefsByTaskTypeKeys(playing.keySet())) {
            audioByType.computeIfAbsent((String) row[0], key -> new ArrayList<>()).add((UUID) row[1]);
            allAudio.add((UUID) row[1]);
        }
        Map<UUID, Integer> durations = mediaService.durationSeconds(allAudio);
        Map<String, TaskTypeStimulusResponse> result = new HashMap<>();
        playing.forEach((key, type) -> {
            List<Integer> measured = audioByType.getOrDefault(key, List.of()).stream()
                    .map(durations::get).filter(java.util.Objects::nonNull).toList();
            result.put(key, new TaskTypeStimulusResponse(key,
                    StimulusLength.median(measured, type.getStimulusEstimateSeconds()), measured.size()));
        });
        return Map.copyOf(result);
    }
}
