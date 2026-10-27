package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeStudentProfile;
import com.pte.practice.internal.dto.request.PracticeProfileUpdateRequest;
import com.pte.practice.internal.dto.response.profile.PracticeProfileResponse;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.practice.internal.exception.PracticeProfileException;
import com.pte.practice.internal.repository.PracticeStudentProfileRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeProfileServiceTest {

    @Mock
    private PracticeStudentProfileRepository profileRepository;

    @Mock
    private PracticeEntitlementService entitlementService;

    private PracticeProfileService service;
    private CurrentUser caller;
    private final UUID studentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // Fix clock at 2026-10-25 08:00:00 UTC (15:00:00 in Asia/Ho_Chi_Minh on 2026-10-25)
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-10-25T08:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void setUp() {
        caller = new CurrentUser(studentId, tenantId, List.of("STUDENT"));
        service = new PracticeProfileService(profileRepository, entitlementService, clock);
    }

    @Test
    void get_whenNoProfileExists_returnsNullExamDate() {
        when(profileRepository.findByStudentPublicIdAndDeletedFalse(studentId))
                .thenReturn(Optional.empty());

        PracticeProfileResponse response = service.get(caller);

        verify(entitlementService).assertUnlocked(caller, tenantId);
        assertThat(response.examDate()).isNull();
    }

    @Test
    void get_whenProfileExists_returnsSavedExamDate() {
        PracticeStudentProfile profile = new PracticeStudentProfile();
        profile.setStudentPublicId(studentId);
        profile.setTenantId(tenantId);
        profile.setExamDate(LocalDate.of(2026, 11, 7));

        when(profileRepository.findByStudentPublicIdAndDeletedFalse(studentId))
                .thenReturn(Optional.of(profile));

        PracticeProfileResponse response = service.get(caller);

        verify(entitlementService).assertUnlocked(caller, tenantId);
        assertThat(response.examDate()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void get_whenEntitlementLocked_throwsException() {
        doThrow(new PracticeNotEntitledException())
                .when(entitlementService).assertUnlocked(caller, tenantId);

        assertThatThrownBy(() -> service.get(caller))
                .isInstanceOf(PracticeNotEntitledException.class);
    }

    @Test
    void update_withValidDate_createsNewProfileAndSaves() {
        LocalDate validDate = LocalDate.of(2026, 11, 7);
        when(profileRepository.findByStudentPublicIdAndDeletedFalse(studentId))
                .thenReturn(Optional.empty());
        when(profileRepository.saveAndFlush(any(PracticeStudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeProfileResponse response = service.update(
                caller, new PracticeProfileUpdateRequest(validDate));

        verify(entitlementService).assertUnlocked(caller, tenantId);
        ArgumentCaptor<PracticeStudentProfile> captor = ArgumentCaptor.forClass(PracticeStudentProfile.class);
        verify(profileRepository).saveAndFlush(captor.capture());

        PracticeStudentProfile saved = captor.getValue();
        assertThat(saved.getStudentPublicId()).isEqualTo(studentId);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getExamDate()).isEqualTo(validDate);
        assertThat(response.examDate()).isEqualTo(validDate);
    }

    @Test
    void update_withValidDateToday_succeeds() {
        // Today is 2026-10-25 in Asia/Ho_Chi_Minh
        LocalDate today = LocalDate.of(2026, 10, 25);
        when(profileRepository.findByStudentPublicIdAndDeletedFalse(studentId))
                .thenReturn(Optional.empty());
        when(profileRepository.saveAndFlush(any(PracticeStudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeProfileResponse response = service.update(
                caller, new PracticeProfileUpdateRequest(today));

        assertThat(response.examDate()).isEqualTo(today);
    }

    @Test
    void update_withDateInPast_throws422() {
        LocalDate yesterday = LocalDate.of(2026, 10, 24);

        assertThatThrownBy(() -> service.update(caller, new PracticeProfileUpdateRequest(yesterday)))
                .isInstanceOf(PracticeProfileException.class)
                .satisfies(ex -> {
                    PracticeProfileException ppe = (PracticeProfileException) ex;
                    assertThat(ppe.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ppe.getCode()).isEqualTo(PracticeConstants.PRACTICE_EXAM_DATE_INVALID);
                });

        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_withDateMoreThanTwoYearsInFuture_throws422() {
        // Max allowed is 2028-10-25
        LocalDate tooFar = LocalDate.of(2028, 10, 26);

        assertThatThrownBy(() -> service.update(caller, new PracticeProfileUpdateRequest(tooFar)))
                .isInstanceOf(PracticeProfileException.class)
                .satisfies(ex -> {
                    PracticeProfileException ppe = (PracticeProfileException) ex;
                    assertThat(ppe.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ppe.getCode()).isEqualTo(PracticeConstants.PRACTICE_EXAM_DATE_INVALID);
                });

        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_withNullDate_clearsExistingExamDate() {
        PracticeStudentProfile profile = new PracticeStudentProfile();
        profile.setStudentPublicId(studentId);
        profile.setTenantId(tenantId);
        profile.setExamDate(LocalDate.of(2026, 11, 7));

        when(profileRepository.findByStudentPublicIdAndDeletedFalse(studentId))
                .thenReturn(Optional.of(profile));
        when(profileRepository.saveAndFlush(any(PracticeStudentProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeProfileResponse response = service.update(
                caller, new PracticeProfileUpdateRequest(null));

        verify(entitlementService).assertUnlocked(caller, tenantId);
        assertThat(profile.getExamDate()).isNull();
        assertThat(response.examDate()).isNull();
    }
}
