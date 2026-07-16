package com.aptis.modules.examdelivery.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.dto.RedactedQuestionResponse;
import com.aptis.modules.examdelivery.dto.SectionSummaryResponse;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.domain.ExamQuestion;
import com.aptis.modules.examoperations.domain.SessionTimeOverride;
import com.aptis.modules.examoperations.repository.ExamQuestionRepository;
import com.aptis.modules.examoperations.repository.SessionTimeOverrideRepository;
import com.aptis.modules.iam.repository.HostRepository;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.enums.Skill;
import com.aptis.modules.questionbank.repository.QuestionRepository;
import com.aptis.modules.tenancy.repository.OrganizationRepository;

/**
 * Student-facing content delivery: skill-subset filtering, availability-window enforcement,
 * answer stripping, and defensive tenant scoping (a Host-authored question can only be
 * served if it belongs to the same tenant that owns the exam it's attached to).
 */
@Service
public class ExamContentDeliveryService {

    private final ExamAttemptAccessGuard accessGuard;
    private final ExamQuestionRepository examQuestionRepository;
    private final SessionTimeOverrideRepository timeOverrideRepository;
    private final QuestionRepository questionRepository;
    private final AssetOperations assetOperations;
    private final HostRepository hostRepository;
    private final OrganizationRepository organizationRepository;

    public ExamContentDeliveryService(
            ExamAttemptAccessGuard accessGuard,
            ExamQuestionRepository examQuestionRepository,
            SessionTimeOverrideRepository timeOverrideRepository,
            QuestionRepository questionRepository,
            AssetOperations assetOperations,
            HostRepository hostRepository,
            OrganizationRepository organizationRepository) {
        this.accessGuard = accessGuard;
        this.examQuestionRepository = examQuestionRepository;
        this.timeOverrideRepository = timeOverrideRepository;
        this.questionRepository = questionRepository;
        this.assetOperations = assetOperations;
        this.hostRepository = hostRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public List<SectionSummaryResponse> getSections(Long attemptId, JwtPrincipal principal) {
        ExamAttempt attempt = accessGuard.loadOwnedAttempt(attemptId, principal);
        Exam exam = attempt.getExam();
        accessGuard.checkAvailabilityWindow(exam);

        List<Question> visibleQuestions = loadVisibleQuestions(exam);
        List<SessionTimeOverride> overrides = timeOverrideRepository.findByExamId(exam.getId());
        Map<Skill, Integer> overrideMinutesBySkill = overrides.stream()
                .collect(Collectors.groupingBy(
                        SessionTimeOverride::getSkill,
                        Collectors.summingInt(SessionTimeOverride::getOverrideMinutes)));

        Map<Skill, Long> countBySkill = visibleQuestions.stream()
                .collect(Collectors.groupingBy(Question::getSkill, Collectors.counting()));

        List<SectionSummaryResponse> sections = new ArrayList<>();
        for (Map.Entry<Skill, Long> entry : countBySkill.entrySet()) {
            Skill skill = entry.getKey();
            sections.add(new SectionSummaryResponse(
                    skill.name(),
                    entry.getValue().intValue(),
                    overrideMinutesBySkill.get(skill)));
        }
        sections.sort(Comparator.comparing(SectionSummaryResponse::skill));
        return sections;
    }

    @Transactional(readOnly = true)
    public List<RedactedQuestionResponse> getSectionQuestions(Long attemptId, String skillParam, JwtPrincipal principal) {
        ExamAttempt attempt = accessGuard.loadOwnedAttempt(attemptId, principal);
        Exam exam = attempt.getExam();
        accessGuard.checkAvailabilityWindow(exam);

        Skill skill = parseSkill(skillParam);
        if (!isSkillEnabled(exam, skill)) {
            // 404: the section does not exist for this session's configuration.
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, ExamDeliveryConstants.SKILL_NOT_IN_SESSION_SUBSET);
        }

        List<Question> visibleQuestions = loadVisibleQuestions(exam);
        Map<Long, Integer> sequenceByQuestionId = examQuestionRepository.findByExamIdOrderBySequenceAsc(exam.getId())
                .stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, ExamQuestion::getSequence, (a, b) -> a));

        List<Question> sectionQuestions = visibleQuestions.stream()
                .filter(q -> q.getSkill() == skill)
                .sorted(Comparator.comparing(q -> sequenceByQuestionId.getOrDefault(q.getId(), Integer.MAX_VALUE)))
                .toList();

        Map<UUID, AssetResponse> audioAssetsById = loadAudioAssets(sectionQuestions);

        return sectionQuestions.stream()
                .map(q -> toRedactedResponse(q, sequenceByQuestionId.get(q.getId()), audioAssetsById))
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────

    /** Every ExamQuestion→Question for this exam, filtered by skill-subset AND tenant scoping. */
    private List<Question> loadVisibleQuestions(Exam exam) {
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(exam.getId());
        List<Long> questionIds = examQuestions.stream()
                .map(ExamQuestion::getQuestionId)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return List.of();
        }

        UUID examOwnerTenantId = resolveExamOwnerTenantId(exam);

        return questionRepository.findAllById(questionIds).stream()
                .filter(q -> isSkillEnabled(exam, q.getSkill()))
                .filter(q -> isVisibleToTenant(q, examOwnerTenantId))
                .toList();
    }

    /** A question is visible if it's Vendor-shared (tenantId null) or scoped to the exam's own tenant. */
    private boolean isVisibleToTenant(Question question, UUID examOwnerTenantId) {
        if (question.getTenantId() == null) {
            return true;
        }
        return question.getTenantId().equals(examOwnerTenantId);
    }

    private UUID resolveExamOwnerTenantId(Exam exam) {
        try {
            Long hostId = Long.valueOf(exam.getHostId());
            Long organizationId = hostRepository.findById(hostId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .getOrganizationId();
            return organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .getPublicId();
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private boolean isSkillEnabled(Exam exam, Skill skill) {
        return switch (skill) {
            case READING -> Boolean.TRUE.equals(exam.getSkillSubsetReading());
            case LISTENING -> Boolean.TRUE.equals(exam.getSkillSubsetListening());
            case WRITING -> Boolean.TRUE.equals(exam.getSkillSubsetWriting());
            case SPEAKING -> Boolean.TRUE.equals(exam.getSkillSubsetSpeaking());
            // Enabling skills (Grammar, Vocabulary, Oral Fluency, Pronunciation, Spelling,
            // Written Discourse) aren't independently gated — they ride along with whichever
            // communicative-skill task produced them.
            case GRAMMAR, VOCABULARY, ORAL_FLUENCY, PRONUNCIATION, SPELLING, WRITTEN_DISCOURSE -> true;
        };
    }

    private Skill parseSkill(String skillParam) {
        try {
            return Skill.valueOf(skillParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid skill: " + skillParam);
        }
    }

    private Map<UUID, AssetResponse> loadAudioAssets(List<Question> questions) {
        List<UUID> audioAssetIds = questions.stream()
                .map(Question::getAudioAssetId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (audioAssetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AssetResponse> byId = new HashMap<>();
        for (AssetResponse asset : assetOperations.getAssets(audioAssetIds)) {
            byId.put(asset.publicId(), asset);
        }
        return byId;
    }

    private RedactedQuestionResponse toRedactedResponse(
            Question question, Integer sequence, Map<UUID, AssetResponse> audioAssetsById) {
        AssetResponse audio = question.getAudioAssetId() != null
                ? audioAssetsById.get(question.getAudioAssetId())
                : null;

        return new RedactedQuestionResponse(
                question.getPublicId(),
                question.getSkill().name(),
                question.getPart(),
                question.getQuestionType().name(),
                question.getContent(),
                question.getInstruction(),
                question.getTimeLimit(),
                question.getPrepTime(),
                question.getMaxPlayCount(),
                question.getOptions(),
                audio != null ? audio.cdnUrl() : null,
                sequence);
    }
}
