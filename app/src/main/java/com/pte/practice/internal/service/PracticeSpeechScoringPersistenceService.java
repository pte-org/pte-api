package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.scoring.PracticeAiEvaluation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

/** Keeps speech-score reads and writes in short transactions around provider work. */
@Service
public class PracticeSpeechScoringPersistenceService {

    private final PracticeSessionItemRepository itemRepository;
    private final JsonMapper jsonMapper;

    public PracticeSpeechScoringPersistenceService(PracticeSessionItemRepository itemRepository,
            JsonMapper jsonMapper) {
        this.itemRepository = itemRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<SpeechScoringInput> load(UUID itemPublicId) {
        PracticeSessionItem item = itemRepository.findByPublicIdAndDeletedFalse(itemPublicId).orElse(null);
        if (item == null || item.getStatus() != PracticeSessionItemStatus.ANSWERED
                || item.getSavedPayload() == null || item.getSavedPayload().isBlank()) {
            return Optional.empty();
        }
        try {
            String mediaPublicId = jsonMapper.readTree(item.getSavedPayload()).path("mediaPublicId").asText(null);
            if (mediaPublicId == null || mediaPublicId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new SpeechScoringInput(itemPublicId, item.getTaskCode(), mediaPublicId,
                    item.getPromptText(), item.getReferenceAnswerText()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(PracticeAiEvaluation result, UUID itemPublicId) {
        PracticeSessionItem item = itemRepository.findByPublicIdAndDeletedFalse(itemPublicId).orElse(null);
        if (item == null || item.getStatus() != PracticeSessionItemStatus.ANSWERED) {
            return;
        }
        item.setAiScore(result.rawScore());
        item.setAiScoreStatus(result.scoreStatus());
        item.setAiProvider(result.provider());
        item.setAiModel(result.model());
        item.setAiProviderVersion(result.providerVersion());
        item.setAiTranscript(result.transcript());
        item.setAiDetailJson(result.detailJson());
        item.setAiScoredAt(result.evaluatedAt());
        item.setAiResultHash(result.resultHash());
        try {
            item.setAiSubScoresJson(jsonMapper.writeValueAsString(result.subScores()));
        } catch (RuntimeException exception) {
            item.setAiSubScoresJson(null);
        }
        itemRepository.saveAndFlush(item);
    }

    public record SpeechScoringInput(UUID itemPublicId, String taskCode, String mediaPublicId,
            String promptText, String referenceAnswerText) { }
}
