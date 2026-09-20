package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.ExamSnapshot;
import com.pte.assessment.domain.SnapshotItem;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.EmptyBlueprintException;
import com.pte.assessment.internal.mapper.SnapshotMapper;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.assessment.internal.repository.ExamSnapshotRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/**
 * Freezes a blueprint into an immutable, versioned {@link ExamSnapshot} by
 * DEEP-COPYING each question's content (including options serialized to JSON,
 * already in delivery order from {@code ItembankService}) so a
 * published snapshot never changes when source questions are later edited.
 *
 * <p>No outbox/event emission (plan.md's forbidden-artifact list) — in the
 * monolith, {@code session} reads a published snapshot through
 * {@link com.pte.assessment.AssessmentService#getSummary} directly, an
 * in-process call rather than an eventually-consistent projection.
 */
@Service
public class SnapshotPublishService {

    private final ExamBlueprintRepository blueprintRepository;
    private final ExamSnapshotRepository snapshotRepository;
    private final ItembankService itembankService;
    private final AssessmentAccessPolicy accessPolicy;
    private final JsonMapper jsonMapper;
    private final ScoreTemplateService scoreTemplateService;

    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, ExamSnapshotRepository snapshotRepository,
                                  ItembankService itembankService, AssessmentAccessPolicy accessPolicy,
                                  JsonMapper jsonMapper, ScoreTemplateService scoreTemplateService) {
        this.blueprintRepository = blueprintRepository;
        this.snapshotRepository = snapshotRepository;
        this.itembankService = itembankService;
        this.accessPolicy = accessPolicy;
        this.jsonMapper = jsonMapper;
        this.scoreTemplateService = scoreTemplateService;
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
        // Resolved once per publish, before any mutation, so a missing ACTIVE
        // template (NoActiveScoreTemplateException, not caught here — it must
        // surface as a loud configuration error) leaves the blueprint DRAFT and
        // saves no snapshot (spec FR-13's immutable pin starts from a real value).
        ScoreTemplateResponse activeTemplate = scoreTemplateService.getActive();

        int version = (int) snapshotRepository.countBySourceBlueprintPublicId(blueprintPublicId) + 1;
        ExamSnapshot snapshot = new ExamSnapshot();
        snapshot.setName(blueprint.getName());
        snapshot.setVersion(version);
        snapshot.setSourceBlueprintPublicId(blueprintPublicId);
        snapshot.setScoreTemplatePublicId(activeTemplate.publicId());
        snapshot.setScoreTemplateVersion(activeTemplate.version());
        snapshot.setTenantId(blueprint.getTenantId());
        blueprint.getItems().forEach(item -> snapshot.addItem(toSnapshotItem(
                itembankService.freeze(item.getQuestionPublicId()), item.getSection(), item.getOrderIndex())));

        ExamSnapshot saved = snapshotRepository.save(snapshot);
        blueprint.setStatus(BlueprintStatus.PUBLISHED);
        blueprintRepository.save(blueprint);
        return SnapshotMapper.toResponse(saved);
    }

    /**
     * Full-fidelity content for the trusted application-call surface (called by
     * {@code attempt} at attempt-create). No {@link CurrentUser} check here —
     * per-student entitlement is gated by {@code session} before this call.
     */
    @Transactional(readOnly = true)
    public SnapshotContentResponse getContent(UUID publicId) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        return SnapshotMapper.toContentResponse(snapshot);
    }

    /**
     * Answer-stripped summary for the trusted application-call surface (called
     * by {@code session} at session-creation time). Same shape as {@link #get},
     * but no {@link CurrentUser}/tenant-visibility check — a session's
     * composition is validated against {@code session}'s own entitlement rules,
     * not assessment's per-tenant visibility.
     */
    @Transactional(readOnly = true)
    public SnapshotResponse getSummary(UUID publicId) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        return SnapshotMapper.toResponse(snapshot);
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

    private SnapshotItem toSnapshotItem(QuestionFreezeView question, PteSection section, int orderIndex) {
        SnapshotItem item = new SnapshotItem();
        item.setSourceQuestionPublicId(question.sourceQuestionPublicId());
        item.setPteTaskType(question.pteTaskType());
        item.setSection(section);
        item.setOrderIndex(orderIndex);
        item.setTitle(question.title());
        item.setPromptText(question.promptText());
        item.setAudioPromptRef(question.audioPromptRef());
        item.setImagePromptRef(question.imagePromptRef());
        item.setReferenceAnswerText(question.referenceAnswerText());
        item.setCorrectAnswerText(question.correctAnswerText());
        item.setMinWordCount(question.minWordCount());
        item.setMaxWordCount(question.maxWordCount());
        item.setOptionsJson(serializeOptions(question.options()));
        return item;
    }

    private String serializeOptions(List<QuestionFreezeView.Option> options) {
        List<FrozenOption> frozen = options.stream()
                .map(o -> new FrozenOption(o.text(), o.correct(), o.orderIndex(), o.blankIndex(), o.correctGapIndex()))
                .toList();
        try {
            return jsonMapper.writeValueAsString(frozen);
        } catch (JacksonException ex) {
            throw new IllegalStateException(AssessmentConstants.SNAPSHOT_OPTIONS_SERIALIZATION_FAILED, ex);
        }
    }

    /**
     * Frozen option shape stored in {@code SnapshotItem.optionsJson}.
     * {@code blankIndex} is null except for {@code FILL_IN_THE_BLANKS_DROPDOWN}
     * options, where it groups options under their owning blank; {@code
     * correctGapIndex} is scoring-only, set only for {@code
     * FILL_IN_THE_BLANKS_DRAG_AND_DROP} correct options. {@code attempt}'s own frozen-option
     * reader only needs {@code text}/{@code orderIndex}/{@code blankIndex}
     * (unknown fields deserialize as ignored by default), so the two shapes are
     * not required to stay field-for-field identical — only {@code scoring}'s
     * own local reader (which needs {@code correct}/{@code correctGapIndex} for
     * grading) must match this shape exactly.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
