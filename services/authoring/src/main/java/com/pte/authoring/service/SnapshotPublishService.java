package com.pte.authoring.service;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.authoring.domain.BlueprintItem;
import com.pte.authoring.domain.ExamBlueprint;
import com.pte.authoring.domain.ExamSnapshot;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.SnapshotItem;
import com.pte.authoring.domain.enums.BlueprintStatus;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.event.ExamSnapshotPublishedEvent;
import com.pte.authoring.domain.exception.BlueprintNotFoundException;
import com.pte.authoring.domain.exception.EmptyBlueprintException;
import com.pte.authoring.domain.exception.QuestionNotFoundException;
import com.pte.authoring.dto.response.SnapshotContentResponse;
import com.pte.authoring.dto.response.SnapshotResponse;
import com.pte.authoring.mapper.SnapshotMapper;
import com.pte.authoring.messaging.outbox.OutboxWriter;
import com.pte.authoring.repository.ExamBlueprintRepository;
import com.pte.authoring.repository.ExamSnapshotRepository;
import com.pte.authoring.repository.QuestionRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
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
    private final JsonMapper jsonMapper;

    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, QuestionRepository questionRepository,
                                  ExamSnapshotRepository snapshotRepository, AuthoringAccessPolicy accessPolicy,
                                  OutboxWriter outboxWriter, JsonMapper jsonMapper) {
        this.blueprintRepository = blueprintRepository;
        this.questionRepository = questionRepository;
        this.snapshotRepository = snapshotRepository;
        this.accessPolicy = accessPolicy;
        this.outboxWriter = outboxWriter;
        this.jsonMapper = jsonMapper;
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

    /**
     * Full-fidelity content for the internal service-to-service surface (called
     * by exam-delivery at attempt-create). No {@link CurrentUser} check here —
     * the caller is authenticated as {@code ROLE_INTERNAL_SERVICE}, not a human;
     * per-student entitlement was already gated by scheduling before this call.
     */
    @Transactional(readOnly = true)
    public SnapshotContentResponse getContent(UUID publicId) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        return SnapshotMapper.toContentResponse(snapshot);
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
        List<FrozenOption> options = deliveryOrder(question).stream()
                .map(o -> new FrozenOption(o.getText(), o.isCorrect(), o.getOrderIndex(), o.getBlankIndex(), o.getCorrectGapIndex()))
                .toList();
        try {
            return jsonMapper.writeValueAsString(options);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize snapshot options", ex);
        }
    }

    /**
     * {@code Question.options} is JPA-mapped {@code @OrderBy("orderIndex ASC")},
     * so {@code question.getOptions()} always returns options already sorted by
     * their {@code orderIndex} identity — correct for every options-based type
     * where {@code orderIndex} only needs to be a stable choice identity (MC
     * types, the shared fill-blanks word bank). But {@code RE_ORDER_PARAGRAPHS}
     * specifically needs {@code orderIndex} to also be the CORRECT final
     * position, with students shown a shuffled arrangement to rearrange back —
     * serving the natural (already-ascending) order would deliver every
     * paragraph already correctly placed, making the task trivially solved
     * without any rearranging. A fixed single-position rotation (guaranteed to
     * move every option to a different array index whenever there are 2+
     * options) breaks that alignment deterministically; this is a minimal
     * correctness fix, not a randomization/authoring-UX feature — a real
     * per-question shuffle strategy is a future authoring concern.
     */
    List<QuestionOption> deliveryOrder(Question question) {
        List<QuestionOption> natural = question.getOptions();
        if (question.getPteTaskType() != PteTaskType.RE_ORDER_PARAGRAPHS || natural.size() < 2) {
            return natural;
        }
        List<QuestionOption> rotated = new ArrayList<>(natural);
        Collections.rotate(rotated, 1);
        return rotated;
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

    /**
     * Frozen option shape stored in {@code SnapshotItem.optionsJson}.
     * {@code blankIndex} is null except for {@code FILL_BLANKS_READING_WRITING}
     * options, where it groups options under their owning blank; {@code
     * correctGapIndex} is scoring-only, set only for {@code
     * FILL_BLANKS_READING} correct options — see {@code QuestionOption}'s doc
     * comment for both. exam-delivery's {@code AttemptMapper.FrozenOption}
     * reads this same JSON but only needs {@code text}/{@code orderIndex}/
     * {@code blankIndex} (unknown fields deserialize as ignored by default),
     * so the two records are not required to stay field-for-field identical
     * — only {@code scoring}'s own local {@code FrozenOption} (which reads
     * {@code correct}/{@code correctGapIndex} for grading) must match this
     * shape exactly.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
