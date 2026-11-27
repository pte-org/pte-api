package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeStudentProfile;
import com.pte.practice.internal.dto.request.PracticeAvatarUpdateRequest;
import com.pte.practice.internal.dto.request.PracticeProfileUpdateRequest;
import com.pte.practice.internal.dto.response.profile.PracticeProfileResponse;
import com.pte.practice.internal.exception.PracticeProfileException;
import com.pte.practice.internal.repository.PracticeStudentProfileRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Manages practice student profile learning targets and preferences (such as the target exam date).
 */
@Service
public class PracticeProfileService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PracticeStudentProfileRepository profileRepository;
    private final PracticeEntitlementService entitlementService;
    private final Clock clock;

    public PracticeProfileService(
            PracticeStudentProfileRepository profileRepository,
            PracticeEntitlementService entitlementService,
            Clock clock) {
        this.profileRepository = profileRepository;
        this.entitlementService = entitlementService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PracticeProfileResponse get(CurrentUser caller) {
        entitlementService.assertUnlocked(caller, caller != null ? caller.tenantId() : null);
        if (caller == null || caller.userId() == null) {
            return new PracticeProfileResponse(null, null);
        }
        return profileRepository.findByStudentPublicIdAndDeletedFalse(caller.userId())
                .map(profile -> new PracticeProfileResponse(profile.getExamDate(), profile.getGender()))
                .orElseGet(() -> new PracticeProfileResponse(null, null));
    }

    @Transactional
    public PracticeProfileResponse update(CurrentUser caller, PracticeProfileUpdateRequest request) {
        entitlementService.assertUnlocked(caller, caller != null ? caller.tenantId() : null);
        if (caller == null || caller.userId() == null) {
            return new PracticeProfileResponse(null, null);
        }

        LocalDate examDate = request != null ? request.examDate() : null;
        if (examDate != null) {
            LocalDate today = clock.instant().atZone(VIETNAM_ZONE).toLocalDate();
            LocalDate maxDate = today.plusYears(2);
            if (examDate.isBefore(today) || examDate.isAfter(maxDate)) {
                throw new PracticeProfileException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        PracticeConstants.PRACTICE_EXAM_DATE_INVALID,
                        PracticeConstants.PRACTICE_EXAM_DATE_INVALID_MESSAGE);
            }
        }

        PracticeStudentProfile profile = profileRepository.findByStudentPublicIdAndDeletedFalse(caller.userId())
                .orElseGet(() -> {
                    PracticeStudentProfile newProfile = new PracticeStudentProfile();
                    newProfile.setStudentPublicId(caller.userId());
                    newProfile.setTenantId(caller.tenantId());
                    return newProfile;
                });

        profile.setExamDate(examDate);

        try {
            profile = profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            profile = profileRepository.findByStudentPublicIdAndDeletedFalse(caller.userId())
                    .orElseThrow(() -> ex);
            profile.setExamDate(examDate);
            profile = profileRepository.save(profile);
        }

        return new PracticeProfileResponse(profile.getExamDate(), profile.getGender());
    }

    /**
     * Saves the avatar and nothing else: it reads the profile, sets the one field and writes it back, so the exam
     * date is carried through untouched. It is its own method, apart from {@link #update}, on purpose.
     */
    @Transactional
    public PracticeProfileResponse updateAvatar(CurrentUser caller, PracticeAvatarUpdateRequest request) {
        entitlementService.assertUnlocked(caller, caller != null ? caller.tenantId() : null);
        if (caller == null || caller.userId() == null) {
            return new PracticeProfileResponse(null, null);
        }
        PracticeStudentProfile profile = profileRepository.findByStudentPublicIdAndDeletedFalse(caller.userId())
                .orElseGet(() -> {
                    PracticeStudentProfile newProfile = new PracticeStudentProfile();
                    newProfile.setStudentPublicId(caller.userId());
                    newProfile.setTenantId(caller.tenantId());
                    return newProfile;
                });
        profile.setGender(request.gender());
        try {
            profile = profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            // Two first saves raced: the other one created the row, so set the avatar on that one.
            profile = profileRepository.findByStudentPublicIdAndDeletedFalse(caller.userId())
                    .orElseThrow(() -> ex);
            profile.setGender(request.gender());
            profile = profileRepository.save(profile);
        }
        return new PracticeProfileResponse(profile.getExamDate(), profile.getGender());
    }
}
