package com.aptis.modules.examoperations.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.domain.SessionTimeOverride;
import com.aptis.modules.examoperations.dto.CreateExamRequest;
import com.aptis.modules.examoperations.dto.ExamDetailResponse;
import com.aptis.modules.examoperations.dto.ExamSettingsRequest;
import com.aptis.modules.examoperations.dto.TimeOverrideRequest;
import com.aptis.modules.examoperations.dto.TimeOverrideResponse;
import com.aptis.modules.examoperations.interfaces.ExamOperations;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.examoperations.repository.SessionTimeOverrideRepository;
import com.aptis.modules.iam.domain.Proctor;
import com.aptis.modules.iam.repository.ProctorRepository;
import com.aptis.modules.questionbank.domain.Skill;

@Service
public class ExamService implements ExamOperations {

    private final ExamRepository examRepository;
    private final SessionTimeOverrideRepository timeOverrideRepository;
    private final ProctorRepository proctorRepository;

    public ExamService(
            ExamRepository examRepository,
            SessionTimeOverrideRepository timeOverrideRepository,
            ProctorRepository proctorRepository) {
        this.examRepository = examRepository;
        this.timeOverrideRepository = timeOverrideRepository;
        this.proctorRepository = proctorRepository;
    }

    @Override
    @Transactional
    public ExamDetailResponse createExam(CreateExamRequest request, JwtPrincipal principal) {
        validateSettings(request.settings());

        Exam exam = Exam.create(request.name(), request.code(), principal.userId().toString());
        applySettings(exam, request.settings());
        Exam saved = examRepository.save(exam);

        List<SessionTimeOverride> overrides = saveTimeOverrides(saved, request.settings().timeOverrides());
        return toDetailResponse(saved, overrides);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamDetailResponse getExam(Long examId, JwtPrincipal principal) {
        Exam exam = findOwnedExam(examId, principal);
        List<SessionTimeOverride> overrides = timeOverrideRepository.findByExamId(exam.getId());
        return toDetailResponse(exam, overrides);
    }

    @Override
    @Transactional
    public ExamDetailResponse updateSettings(Long examId, ExamSettingsRequest request, JwtPrincipal principal) {
        validateSettings(request);

        Exam exam = findOwnedExam(examId, principal);
        applySettings(exam, request);
        Exam saved = examRepository.save(exam);

        timeOverrideRepository.deleteByExamId(saved.getId());
        List<SessionTimeOverride> overrides = saveTimeOverrides(saved, request.timeOverrides());
        return toDetailResponse(saved, overrides);
    }

    @Override
    @Transactional
    public ExamDetailResponse assignProctor(Long examId, Long proctorId, JwtPrincipal principal) {
        Exam exam = findOwnedExam(examId, principal);

        // Tenant check: the proctor must belong to the same organization as the assigning Host.
        Proctor proctor = proctorRepository.findByIdAndOrganizationId(proctorId, principal.tenantId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        exam.setProctorId(proctor.getId());
        Exam saved = examRepository.save(exam);
        List<SessionTimeOverride> overrides = timeOverrideRepository.findByExamId(saved.getId());
        return toDetailResponse(saved, overrides);
    }

    @Override
    @Transactional
    public ExamDetailResponse unassignProctor(Long examId, JwtPrincipal principal) {
        Exam exam = findOwnedExam(examId, principal);
        exam.setProctorId(null);
        Exam saved = examRepository.save(exam);
        List<SessionTimeOverride> overrides = timeOverrideRepository.findByExamId(saved.getId());
        return toDetailResponse(saved, overrides);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private Exam findOwnedExam(Long examId, JwtPrincipal principal) {
        String hostId = principal.userId().toString();
        return examRepository.findByIdAndHostId(examId, hostId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateSettings(ExamSettingsRequest request) {
        boolean anySkillEnabled = Boolean.TRUE.equals(request.skillSubsetReading())
                || Boolean.TRUE.equals(request.skillSubsetListening())
                || Boolean.TRUE.equals(request.skillSubsetWriting())
                || Boolean.TRUE.equals(request.skillSubsetSpeaking());
        if (!anySkillEnabled) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "At least one skill must be enabled");
        }

        OffsetDateTime start = request.availabilityStart();
        OffsetDateTime end = request.availabilityEnd();
        if (start != null && end != null && !start.isBefore(end)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "availabilityStart must be before availabilityEnd");
        }
        if (end != null && end.isBefore(OffsetDateTime.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "availabilityEnd cannot be entirely in the past");
        }
        if (Boolean.TRUE.equals(request.proctorRequired()) && start == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "availabilityStart is required when proctorRequired is true");
        }
    }

    private void applySettings(Exam exam, ExamSettingsRequest request) {
        exam.setSkillSubsetReading(request.skillSubsetReading());
        exam.setSkillSubsetListening(request.skillSubsetListening());
        exam.setSkillSubsetWriting(request.skillSubsetWriting());
        exam.setSkillSubsetSpeaking(request.skillSubsetSpeaking());
        exam.setProctorRequired(request.proctorRequired());
        exam.setAvailabilityStart(request.availabilityStart());
        exam.setAvailabilityEnd(request.availabilityEnd());
        exam.setMaxRetryCount(request.maxRetryCount());
    }

    private List<SessionTimeOverride> saveTimeOverrides(Exam exam, List<TimeOverrideRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<SessionTimeOverride> overrides = requests.stream()
                .map(r -> SessionTimeOverride.create(exam, parseSkill(r.skill()), r.part(), r.overrideMinutes()))
                .toList();
        return timeOverrideRepository.saveAll(overrides);
    }

    private Skill parseSkill(String skill) {
        if (skill == null || skill.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "skill is required in timeOverrides");
        }
        try {
            return Skill.valueOf(skill.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid skill in timeOverrides: " + skill);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStartable(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return isStartable(exam);
    }

    /** A proctor-required exam is startable once a proctor is actually assigned (Phase 7). */
    private boolean isStartable(Exam exam) {
        return !Boolean.TRUE.equals(exam.getProctorRequired()) || exam.getProctorId() != null;
    }

    private ExamDetailResponse toDetailResponse(Exam exam, List<SessionTimeOverride> overrides) {
        List<TimeOverrideResponse> overrideResponses = overrides.stream()
                .map(o -> new TimeOverrideResponse(o.getSkill().name(), o.getPart(), o.getOverrideMinutes()))
                .toList();

        return new ExamDetailResponse(
                exam.getId(),
                exam.getName(),
                exam.getCode(),
                exam.getIsAssignable(),
                exam.getSkillSubsetReading(),
                exam.getSkillSubsetListening(),
                exam.getSkillSubsetWriting(),
                exam.getSkillSubsetSpeaking(),
                exam.getProctorRequired(),
                exam.getAvailabilityStart(),
                exam.getAvailabilityEnd(),
                exam.getMaxRetryCount(),
                exam.getProctorId(),
                overrideResponses,
                isStartable(exam));
    }
}
