package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.draft.DraftParser;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.llm.GeneratorPromptRenderer;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.pipeline.GenerationRequest;
import com.pte.itembank.internal.generation.prompt.PromptContentCodec;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import com.pte.itembank.internal.generation.prompt.PromptTextAssembler;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptTestResponse;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.itembank.internal.generation.review.ReviewPromptBuilder;
import com.pte.itembank.internal.generation.review.ReviewPromptContent;
import com.pte.itembank.internal.generation.review.ReviewVerdictParser;
import com.pte.itembank.internal.generation.validate.DraftValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tries a changed prompt on the real model: it asks for one question with the prompt, then checks that the answer
 * parses and passes the same structure checks a generated question must pass. The result is stored with the version,
 * tied to its content, and is what allows sending the version for approval. The model call runs outside any
 * database transaction because it can take a while.
 */
@Service
public class PromptTestService {

    private static final Set<AiPromptStatus> TESTABLE = Set.of(AiPromptStatus.DRAFT, AiPromptStatus.REJECTED);
    private static final String NOTE_PASSED = "The model answered with valid JSON that passed the structure checks.";
    private static final String NOTE_FAILED_CHECKS = "The answer broke these checks: ";
    private static final String NOTE_FAILED_ANSWER = "The model's answer could not be used: ";
    private static final String NOTE_PROVIDER = "The AI provider failed: ";
    private static final String ISSUE_SEPARATOR = ", ";
    private static final String NOTE_REVIEW_PASSED = "The reviewer answered with a verdict in the expected format.";
    private static final String NOTE_REVIEW_MISSING = "The reviewer's answer left out these checks: ";
    private static final ItemDraft SAMPLE_QUESTION = new ItemDraft.ChoiceDraft(PromptVersionConstants.SAMPLE_TOPIC,
            PromptVersionConstants.SAMPLE_PASSAGE, PromptVersionConstants.SAMPLE_QUESTION,
            PromptVersionConstants.SAMPLE_OPTIONS, List.of(PromptVersionConstants.SAMPLE_CORRECT_INDEX));

    private final AiPromptVersionRepository versions;
    private final PromptContentCodec codec;
    private final PromptRegistry registry;
    private final ObjectProvider<LlmGateway> gateway;
    private final DraftParser draftParser;
    private final DraftValidator draftValidator;
    private final ReviewPromptBuilder reviewPrompts;
    private final ReviewVerdictParser verdictParser;
    private final Clock clock;

    PromptTestService(AiPromptVersionRepository versions, PromptContentCodec codec, PromptRegistry registry,
                      ObjectProvider<LlmGateway> gateway, DraftParser draftParser, DraftValidator draftValidator,
                      ReviewPromptBuilder reviewPrompts, ReviewVerdictParser verdictParser, Clock clock) {
        this.versions = versions;
        this.codec = codec;
        this.registry = registry;
        this.gateway = gateway;
        this.draftParser = draftParser;
        this.draftValidator = draftValidator;
        this.reviewPrompts = reviewPrompts;
        this.verdictParser = verdictParser;
        this.clock = clock;
    }

    public PromptTestResponse run(UUID publicId, CurrentUser caller) {
        AiPromptVersion version = versions.findByPublicId(publicId).orElseThrow(AiPromptVersionQueryService::notFound);
        PromptWorkflowPolicy.editor(caller, version);
        if (!TESTABLE.contains(version.getStatus())) {
            throw new AiPromptException(HttpStatus.CONFLICT, PromptVersionConstants.NOT_EDITABLE,
                    PromptVersionConstants.NOT_EDITABLE_MESSAGE);
        }
        if (!PromptWorkflowPolicy.testable(version.getKind(), version.getPromptKey())) {
            throw new AiPromptException(HttpStatus.BAD_REQUEST, PromptVersionConstants.TEST_NOT_SUPPORTED,
                    PromptVersionConstants.TEST_NOT_SUPPORTED_MESSAGE);
        }
        LlmGateway llm = gateway.getIfAvailable();
        if (llm == null) {
            throw new AiPromptException(HttpStatus.SERVICE_UNAVAILABLE, PromptVersionConstants.TEST_UNAVAILABLE,
                    PromptVersionConstants.TEST_UNAVAILABLE_MESSAGE);
        }
        Outcome outcome = attempt(llm, version);
        version.recordTest(outcome.passed(), truncate(outcome.note(), PromptVersionConstants.MAX_TEST_NOTE_LENGTH),
                caller.userId(), clock.instant());
        try {
            versions.saveAndFlush(version);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException editedMeanwhile) {
            throw new AiPromptException(HttpStatus.CONFLICT, PromptVersionConstants.CONCURRENT_CHANGE,
                    PromptVersionConstants.CONCURRENT_CHANGE_MESSAGE);
        }
        return new PromptTestResponse(outcome.passed(), outcome.note(),
                truncate(outcome.sample(), PromptVersionConstants.MAX_SAMPLE_LENGTH), version.getTestedAt());
    }

    private Outcome attempt(LlmGateway llm, AiPromptVersion version) {
        if (version.getKind() == AiPromptKind.REVIEWER) {
            return attemptReviewer(llm, version);
        }
        String text = PromptTextAssembler.assemble(codec.read(version.getContent()));
        boolean shared = version.getKind() == AiPromptKind.SHARED;
        String taskType = shared ? PromptVersionConstants.SHARED_TEST_TASK_TYPE : version.getPromptKey();
        String common = shared ? text : registry.getCommonPrompt();
        String taskPrompt = shared ? registry.getPrompt(taskType) : text;
        if (common == null || taskPrompt == null) {
            return new Outcome(false, NOTE_FAILED_ANSWER + PromptVersionConstants.VERSION_NOT_FOUND, "");
        }
        GenerationRequest request = new GenerationRequest(null, 0, taskType,
                CefrLevel.valueOf(PromptVersionConstants.TEST_CEFR_LEVEL), QuestionPool.PRACTICE, null, "");
        String answer = "";
        try {
            answer = llm.completeJson(LlmRole.GENERATOR, GeneratorPromptRenderer.render(common, request), taskPrompt);
            ItemDraft draft = draftParser.parse(answer, taskType);
            List<ValidationIssue> issues = draftValidator.validate(draft, taskType);
            return issues.isEmpty() ? new Outcome(true, NOTE_PASSED, answer)
                    : new Outcome(false, NOTE_FAILED_CHECKS + issues.stream().map(ValidationIssue::code).distinct()
                            .collect(Collectors.joining(ISSUE_SEPARATOR)), answer);
        } catch (LlmProviderException providerFailure) {
            return new Outcome(false, NOTE_PROVIDER + providerFailure.getType().code(), answer);
        } catch (DomainException unusable) {
            return new Outcome(false, NOTE_FAILED_ANSWER + unusable.getCode(), answer);
        }
    }

    /** The reviewer judges a small sample question; the test passes when its answer reads as a full verdict. */
    private Outcome attemptReviewer(LlmGateway llm, AiPromptVersion version) {
        String template = PromptTextAssembler.assembleWithoutTitle(codec.read(version.getContent()));
        CefrLevel level = CefrLevel.valueOf(PromptVersionConstants.TEST_CEFR_LEVEL);
        String reply = "";
        try {
            reply = llm.completeJson(LlmRole.REVIEWER,
                    reviewPrompts.render(template, PromptVersionConstants.SHARED_TEST_TASK_TYPE),
                    reviewPrompts.buildUserPrompt(SAMPLE_QUESTION, level, null));
            verdictParser.parse(reply, level);
            return reviewOutcome(reply);
        } catch (LlmProviderException providerFailure) {
            return new Outcome(false, NOTE_PROVIDER + providerFailure.getType().code(), reply);
        } catch (DomainException unusable) {
            return new Outcome(false, NOTE_FAILED_ANSWER + unusable.getCode(), reply);
        }
    }

    private static Outcome reviewOutcome(String reply) {
        String missing = ReviewPromptContent.CHECKS.stream().map(ReviewPromptContent.Check::key)
                .filter(key -> !reply.contains(key)).collect(Collectors.joining(ISSUE_SEPARATOR));
        return missing.isEmpty() ? new Outcome(true, NOTE_REVIEW_PASSED, reply)
                : new Outcome(false, NOTE_REVIEW_MISSING + missing, reply);
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private record Outcome(boolean passed, String note, String sample) {
    }
}
