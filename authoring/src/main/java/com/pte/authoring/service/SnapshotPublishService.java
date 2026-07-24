package com.pte.authoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.authoring.constant.AuthoringConstants;
import com.pte.authoring.domain.BlueprintItem;
import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.domain.ExamSnapshot;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.SnapshotItem;
import com.pte.authoring.domain.enums.BlueprintStatus;
import com.pte.authoring.domain.event.ExamSnapshotPublishedEvent;
import com.pte.authoring.domain.exception.BlueprintNotFoundException;
import com.pte.authoring.domain.exception.EmptyBlueprintException;
import com.pte.authoring.domain.exception.QuestionNotFoundException;
import com.pte.authoring.dto.response.SnapshotResponse;
import com.pte.authoring.mapper.SnapshotMapper;
import com.pte.authoring.messaging.outbox.OutboxWriter;
import com.pte.authoring.repository.ExamBlueprintRepository;
import com.pte.authoring.repository.ExamSnapshotRepository;
import com.pte.authoring.repository.QuestionRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Freezes a blueprint into an immutable, versioned {@link ExamSnapshot} by
 * DEEP-COPYING each question's content (including options serialized to JSON) so a
 * published snapshot never changes when source questions are later edited
 * (ADR-002/003). Emits {@code ExamSnapshotPublished} to the outbox in the same TX.
 */
@Service
public class SnapshotPublishService {

    private final ExamBlueprintRepository blueprintRepository;
    private final QuestionRepository questionRepository;
    private final ExamSnapshotRepository snapshotRepository;
    private final AuthoringAccessPolicy accessPolicy;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, QuestionRepository questionRepository,
                                  ExamSnapshotRepository snapshotRepository, AuthoringAccessPolicy accessPolicy,
                                  OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.blueprintRepository = blueprintRepository;
        this.questionRepository = questionRepository;
        this.snapshotRepository = snapshotRepository;
        this.accessPolicy = accessPolicy;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SnapshotResponse publish(UUID blueprintPublicId, CurrentUser caller) {
        ExamBlueprint blueprint = blueprintRepository.findWithItemsByPublicId(blueprintPublicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(blueprint.getTenantId(), blueprint.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        if (blueprint.getItems().isEmpty()) {
            throw new EmptyBlueprintException();
        }

        int version = (int) snapshotRepository.countBySourceBlueprintPublicId(blueprintPublicId) + 1;
        ExamSnapshot snapshot = new ExamSnapshot();
        snapshot.setName(blueprint.getName());
        snapshot.setVersion(version);
        snapshot.setSourceBlueprintPublicId(blueprintPublicId);
        snapshot.setTenantId(blueprint.getTenantId());
        blueprint.getItems().forEach(item -> snapshot.addItem(freeze(item)));

        ExamSnapshot saved = snapshotRepository.save(snapshot);
        blueprint.setStatus(BlueprintStatus.PUBLISHED);
        blueprintRepository.save(blueprint);
        emitPublished(saved);
        return SnapshotMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SnapshotResponse get(UUID publicId, CurrentUser caller) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(snapshot.getTenantId(), snapshot.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        return SnapshotMapper.toResponse(snapshot);
    }

    private SnapshotItem freeze(BlueprintItem blueprintItem) {
        Question question = questionRepository.findWithOptionsByPublicId(blueprintItem.getQuestionPublicId())
                .orElseThrow(QuestionNotFoundException::new);
        SnapshotItem item = new SnapshotItem();
        item.setSourceQuestionPublicId(question.getPublicId());
        item.setPteTaskType(question.getPteTaskType());
        item.setSection(blueprintItem.getSection());
        item.setOrderIndex(blueprintItem.getOrderIndex());
        item.setTitle(question.getTitle());
        item.setPromptText(question.getPromptText());
        item.setAudioPromptRef(question.getAudioPromptRef());
        item.setImagePromptRef(question.getImagePromptRef());
        item.setReferenceAnswerText(question.getReferenceAnswerText());
        item.setCorrectAnswerText(question.getCorrectAnswerText());
        item.setMinWordCount(question.getMinWordCount());
        item.setMaxWordCount(question.getMaxWordCount());
        item.setOptionsJson(serializeOptions(question));
        return item;
    }

    private String serializeOptions(Question question) {
        List<FrozenOption> options = question.getOptions().stream()
                .map(o -> new FrozenOption(o.getText(), o.isCorrect(), o.getOrderIndex()))
                .toList();
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize snapshot options", ex);
        }
    }

    private void emitPublished(ExamSnapshot snapshot) {
        List<ExamSnapshotPublishedEvent.Item> items = snapshot.getItems().stream()
                .map(i -> new ExamSnapshotPublishedEvent.Item(i.getOrderIndex(), i.getSection(), i.getPteTaskType()))
                .toList();
        ExamSnapshotPublishedEvent event = new ExamSnapshotPublishedEvent(
                snapshot.getPublicId(), snapshot.getVersion(), snapshot.getName(), snapshot.getTenantId(), items);
        outboxWriter.write(AuthoringConstants.AGGREGATE_SNAPSHOT, snapshot.getPublicId().toString(),
                AuthoringConstants.EVENT_SNAPSHOT_PUBLISHED, event, snapshot.getTenantId());
    }

    /** Frozen option shape stored in {@code SnapshotItem.optionsJson}. */
    private record FrozenOption(String text, boolean correct, int orderIndex) {
    }
}
