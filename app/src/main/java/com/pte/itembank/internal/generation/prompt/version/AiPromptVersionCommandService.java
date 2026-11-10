package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.exception.GenerationPromptNotFoundException;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptContentCodec;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptStatusRequest;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionResponse;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptProposalRequest;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Writes to the proposals for changing an editable prompt: save one, and move it through the approval flow
 * (draft, pending, live). Approval makes a version the live one in the same transaction that retires the old one.
 */
@Service
public class AiPromptVersionCommandService {

        private static final Set<AiPromptStatus> EDITABLE = EnumSet.of(AiPromptStatus.DRAFT, AiPromptStatus.REJECTED);

    private final AiPromptVersionRepository versions;
    private final PromptContentCodec codec;
    private final PromptContentValidator validator;
    private final PromptVersionMapper mapper;
    private final Clock clock;

    AiPromptVersionCommandService(AiPromptVersionRepository versions, PromptContentCodec codec,
                                  PromptContentValidator validator, PromptVersionMapper mapper, Clock clock) {
        this.versions = versions;
        this.codec = codec;
        this.validator = validator;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Saves a proposal for changing a prompt: it starts the prompt's proposal when none is open, and otherwise replaces
     * the content of the open one (a rejected one goes back to draft). An earlier test no longer counts.
     */
    @Transactional
    public PromptVersionResponse saveProposal(String category, String key, PromptProposalRequest request,
                                              CurrentUser caller) {
        PromptWorkflowPolicy.author(caller);
        AiPromptKind kind = PromptCategories.editableKind(category);
        String promptKey = PromptCategories.normalizedKey(kind, key);
        List<AiPromptVersion> all = versions.findByKindAndPromptKeyOrderByVersionNoDesc(kind, promptKey);
        AiPromptVersion live = all.stream().filter(version -> version.getStatus() == AiPromptStatus.ACTIVE).findFirst()
                .orElseThrow(GenerationPromptNotFoundException::new);
        PromptContent clean = validator.validated(kind, request.content());
        String hash = codec.hash(clean);
        AiPromptVersion open = PromptProposals.openProposal(all, live).orElse(null);
        if (open == null) {
            if (hash.equals(live.getContentHash())) {
                throw new AiPromptException(HttpStatus.BAD_REQUEST, PromptVersionConstants.NO_CHANGE,
                        PromptVersionConstants.NO_CHANGE_MESSAGE);
            }
            int versionNo = versions.highestVersionNo(kind.name(), promptKey) + 1;
            return mapper.toResponse(saveOrConflict(
                    AiPromptVersion.draft(kind, promptKey, versionNo, codec.write(clean), hash, caller.userId())));
        }
        PromptWorkflowPolicy.editor(caller, open);
        requireStatus(open, EDITABLE, PromptVersionConstants.NOT_EDITABLE, PromptVersionConstants.NOT_EDITABLE_MESSAGE);
        if (request.expectedLockVersion() != null && request.expectedLockVersion() != open.getLockVersion()) {
            throw conflict(PromptVersionConstants.CONCURRENT_CHANGE, PromptVersionConstants.CONCURRENT_CHANGE_MESSAGE);
        }
        open.replaceContent(codec.write(clean), hash, caller.userId());
        return mapper.toResponse(saveOrConflict(open));
    }

    /** One step of the approval flow; each action checks who may take it and which status it needs. */
    @Transactional
    public PromptVersionResponse changeStatus(UUID publicId, PromptStatusRequest request, CurrentUser caller) {
        AiPromptVersion version = load(publicId);
        switch (request.action()) {
            case SUBMIT -> submit(version, caller);
            case APPROVE -> approve(version, caller);
            case REJECT -> reject(version, request.reason(), caller);
            case DISCARD -> discard(version, caller);
        }
        return mapper.toResponse(saveOrConflict(version));
    }

    private void submit(AiPromptVersion version, CurrentUser caller) {
        PromptWorkflowPolicy.editor(caller, version);
        requireStatus(version, Set.of(AiPromptStatus.DRAFT), PromptVersionConstants.TRANSITION_INVALID,
                PromptVersionConstants.TRANSITION_INVALID_MESSAGE);
        if (PromptWorkflowPolicy.testable(version.getKind(), version.getPromptKey()) && !version.hasPassingTest()
                && !PromptWorkflowPolicy.isAdmin(caller)) {
            throw conflict(PromptVersionConstants.TEST_REQUIRED, PromptVersionConstants.TEST_REQUIRED_MESSAGE);
        }
        version.submit(clock.instant());
    }

    /** Approval is activation. An admin may also activate their own draft directly. */
    private void approve(AiPromptVersion version, CurrentUser caller) {
        PromptWorkflowPolicy.reviewer(caller, version);
        Set<AiPromptStatus> allowed = PromptWorkflowPolicy.isAdmin(caller)
                ? EnumSet.of(AiPromptStatus.PENDING, AiPromptStatus.DRAFT) : EnumSet.of(AiPromptStatus.PENDING);
        requireStatus(version, allowed, PromptVersionConstants.TRANSITION_INVALID,
                PromptVersionConstants.TRANSITION_INVALID_MESSAGE);
        versions.retireActive(version.getKind(), version.getPromptKey(), clock.instant());
        version.activate(caller.userId(), clock.instant());
    }

    private void reject(AiPromptVersion version, String reason, CurrentUser caller) {
        PromptWorkflowPolicy.reviewer(caller, version);
        requireStatus(version, Set.of(AiPromptStatus.PENDING), PromptVersionConstants.TRANSITION_INVALID,
                PromptVersionConstants.TRANSITION_INVALID_MESSAGE);
        if (reason == null || reason.isBlank()) {
            throw new AiPromptException(HttpStatus.BAD_REQUEST, PromptVersionConstants.REASON_REQUIRED,
                    PromptVersionConstants.REASON_REQUIRED_MESSAGE);
        }
        version.reject(caller.userId(), reason.strip(), clock.instant());
    }

    private void discard(AiPromptVersion version, CurrentUser caller) {
        PromptWorkflowPolicy.editor(caller, version);
        requireStatus(version, EDITABLE, PromptVersionConstants.TRANSITION_INVALID,
                PromptVersionConstants.TRANSITION_INVALID_MESSAGE);
        version.discard();
    }

    private AiPromptVersion load(UUID publicId) {
        return versions.findByPublicId(publicId).orElseThrow(AiPromptVersionQueryService::notFound);
    }

    private static void requireStatus(AiPromptVersion version, Set<AiPromptStatus> allowed, String code,
                                      String message) {
        if (!allowed.contains(version.getStatus())) {
            throw conflict(code, message);
        }
    }

    /** Saves and flushes now, so a clash with another writer surfaces as the same conflict the checks raise. */
    private AiPromptVersion saveOrConflict(AiPromptVersion version) {
        try {
            return versions.saveAndFlush(version);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException clash) {
            throw conflict(PromptVersionConstants.CONCURRENT_CHANGE, PromptVersionConstants.CONCURRENT_CHANGE_MESSAGE);
        }
    }

    private static AiPromptException conflict(String code, String message) {
        return new AiPromptException(HttpStatus.CONFLICT, code, message);
    }
}
