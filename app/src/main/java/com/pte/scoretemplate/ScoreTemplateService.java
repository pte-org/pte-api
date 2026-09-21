package com.pte.scoretemplate;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateSlotFeasibilityResponse;
import com.pte.scoretemplate.internal.exception.NoActiveScoreTemplateException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotFoundException;
import com.pte.scoretemplate.internal.mapper.ScoreTemplateMapper;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only door other modules use to reach {@code scoretemplate}. {@code
 * ScoreTemplateRepository}, the admin service, and the controller stay in
 * {@code internal/}. Read-only and unauthenticated by design — callers
 * ({@code assessment} pinning at publish, {@code attempt} for timing,
 * {@code scoring} for scoringMethod, {@code reporting} for weights) are
 * trusted in-process application code, not end users; PLATFORM_ADMIN write
 * access is enforced only at {@code ScoreTemplateController}.
 */
@Service
public class ScoreTemplateService {

    private final ScoreTemplateRepository repository;
    private final ItembankService itembankService;

    @Autowired
    public ScoreTemplateService(ScoreTemplateRepository repository, ItembankService itembankService) {
        this.repository = repository;
        this.itembankService = itembankService;
    }

    /** Compatibility constructor for focused read-service tests. */
    public ScoreTemplateService(ScoreTemplateRepository repository) {
        this.repository = repository;
        this.itembankService = null;
    }

    /** Used by {@code assessment.SnapshotPublishService} to pin the scoring scheme at exam-publish time. */
    @Transactional(readOnly = true)
    public ScoreTemplateResponse getActive() {
        ScoreTemplate active = repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)
                .orElseThrow(NoActiveScoreTemplateException::new);
        return ScoreTemplateMapper.toResponse(active);
    }

    /**
     * Used by everything that already has a pinned {@code scoreTemplatePublicId}
     * (attempt/scoring/reporting) — deliberately does NOT filter by status, so
     * a snapshot pinned to a now-RETIRED template still resolves (immutability
     * guarantee: a published exam's scoring never drifts).
     */
    @Transactional(readOnly = true)
    public ScoreTemplateResponse getByPublicId(UUID publicId) {
        ScoreTemplate template = repository.findWithItemsByPublicId(publicId)
                .orElseThrow(ScoreTemplateNotFoundException::new);
        return ScoreTemplateMapper.toResponse(template);
    }

    /** Returns a template only when it is currently ACTIVE, without exposing its internal enum. */
    @Transactional(readOnly = true)
    public Optional<ScoreTemplateResponse> findActiveByPublicId(UUID publicId) {
        return repository.findWithItemsByPublicId(publicId)
                .filter(template -> template.getStatus() == ScoreTemplateStatus.ACTIVE)
                .map(ScoreTemplateMapper::toResponse);
    }

    /** Answer-free catalog readiness report used by authoring and exam orchestration. */
    @Transactional(readOnly = true)
    public ScoreTemplateFeasibilityResponse getTemplateFeasibility(UUID templatePublicId) {
        if (itembankService == null) {
            throw new IllegalStateException("Template feasibility dependencies are not configured");
        }
        ScoreTemplateResponse template = getByPublicId(templatePublicId);
        EnumSet<PteTaskType> taskTypes = EnumSet.noneOf(PteTaskType.class);
        List<ScoreTemplateSlotFeasibilityResponse> slots = new ArrayList<>();
        for (ScoreTemplateItemResponse item : template.items()) {
            try {
                PteTaskType taskType = PteTaskType.valueOf(item.taskType());
                taskTypes.add(taskType);
            } catch (RuntimeException ex) {
                slots.add(new ScoreTemplateSlotFeasibilityResponse(item.taskType(), item.section(),
                        item.maxCount(), 0, false, "UNKNOWN_TASK_TYPE"));
            }
        }
        Map<PteTaskType, Long> availability = itembankService.countPublishedByTaskTypes(taskTypes);
        for (ScoreTemplateItemResponse item : template.items()) {
            try {
                PteTaskType taskType = PteTaskType.valueOf(item.taskType());
                long available = availability.getOrDefault(taskType, 0L);
                boolean sectionMatches = taskType.getSection().name().equals(item.section());
                boolean ready = sectionMatches && available >= item.maxCount();
                String reason = !sectionMatches ? "SECTION_MISMATCH" : ready ? null : "INSUFFICIENT_POOL";
                slots.add(new ScoreTemplateSlotFeasibilityResponse(item.taskType(), item.section(),
                        item.maxCount(), available, ready, reason));
            } catch (RuntimeException ignored) {
                // The invalid slot was already added above with a stable reason.
            }
        }
        return new ScoreTemplateFeasibilityResponse(template.publicId(), template.version(),
                slots.stream().allMatch(ScoreTemplateSlotFeasibilityResponse::ready), slots);
    }
}
