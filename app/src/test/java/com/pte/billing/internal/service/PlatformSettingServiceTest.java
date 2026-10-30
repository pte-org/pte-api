package com.pte.billing.internal.service;

import com.pte.billing.domain.PlatformSetting;
import com.pte.billing.internal.dto.request.PlatformSettingBatchRequest;
import com.pte.billing.internal.dto.request.PlatformSettingChangeRequest;
import com.pte.billing.internal.exception.setting.PlatformSettingInvalidException;
import com.pte.billing.internal.exception.setting.PlatformSettingNotFoundException;
import com.pte.billing.internal.exception.setting.PlatformSettingValidationException;
import com.pte.billing.internal.repository.PlatformSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSettingServiceTest {

    @Mock
    private PlatformSettingRepository platformSettingRepository;

    private PlatformSettingService service;

    @BeforeEach
    void setUp() {
        service = new PlatformSettingService(platformSettingRepository);
    }

    @Test
    void getIntegerCachesTheValue() {
        PlatformSetting setting = setting("free_student_limit", "50");
        when(platformSettingRepository.findByKey("free_student_limit")).thenReturn(Optional.of(setting));

        assertThat(service.getInteger("free_student_limit")).isEqualTo(50);
        assertThat(service.getInteger("free_student_limit")).isEqualTo(50);

        verify(platformSettingRepository, times(1)).findByKey("free_student_limit");
    }

    @Test
    void updateInvalidatesCacheAndReturnsTheNewValue() {
        PlatformSetting setting = setting("free_student_limit", "50");
        when(platformSettingRepository.findByKey("free_student_limit")).thenReturn(Optional.of(setting));
        when(platformSettingRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.getInteger("free_student_limit")).isEqualTo(50);
        service.updateBatch(new PlatformSettingBatchRequest(List.of(
                new PlatformSettingChangeRequest("free_student_limit", "75"))), null);

        assertThat(service.getInteger("free_student_limit")).isEqualTo(75);
        verify(platformSettingRepository, times(3)).findByKey("free_student_limit");
    }

    @Test
    void missingSettingIsAConfigurationError() {
        when(platformSettingRepository.findByKey("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getValue("missing"))
                .isInstanceOf(PlatformSettingNotFoundException.class)
                .satisfies(ex -> assertThat(((PlatformSettingNotFoundException) ex).getStatus().value())
                        .isEqualTo(500));
    }

    @Test
    void nonIntegerSettingIsRejected() {
        when(platformSettingRepository.findByKey("free_student_limit"))
                .thenReturn(Optional.of(setting("free_student_limit", "many")));

        assertThatThrownBy(() -> service.getInteger("free_student_limit"))
                .isInstanceOf(PlatformSettingInvalidException.class);
    }

    @Test
    void updateRejectsValuesOutsideTheCatalogContract() {
        PlatformSetting setting = setting("free_student_limit", "50");
        when(platformSettingRepository.findByKey("free_student_limit")).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> service.updateBatch(new PlatformSettingBatchRequest(List.of(
                new PlatformSettingChangeRequest("free_student_limit", "-1"))), null))
                .isInstanceOf(PlatformSettingValidationException.class);

        verify(platformSettingRepository, org.mockito.Mockito.never()).saveAndFlush(any(PlatformSetting.class));
    }

    @Test
    void batchUpdateValidatesAndReturnsTypedSettings() {
        PlatformSetting freeStudents = setting("free_student_limit", "50");
        PlatformSetting responseSla = setting("tenant_application_response_sla_hours", "72");
        when(platformSettingRepository.findByKey("free_student_limit")).thenReturn(Optional.of(freeStudents));
        when(platformSettingRepository.findByKey("tenant_application_response_sla_hours"))
                .thenReturn(Optional.of(responseSla));
        when(platformSettingRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateBatch(new PlatformSettingBatchRequest(List.of(
                new PlatformSettingChangeRequest("free_student_limit", "75"),
                new PlatformSettingChangeRequest("tenant_application_response_sla_hours", "48"))), null);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).value()).isEqualTo("75");
        assertThat(response.get(0).unit()).isEqualTo("students");
        assertThat(response.get(1).value()).isEqualTo("48");
        assertThat(response.get(1).unit()).isEqualTo("hours");
        verify(platformSettingRepository).saveAllAndFlush(any());
    }

    @Test
    void batchUpdateDoesNotMutateOrPersistWhenAnyValueIsInvalid() {
        PlatformSetting freeStudents = setting("free_student_limit", "50");
        PlatformSetting responseSla = setting("tenant_application_response_sla_hours", "72");
        when(platformSettingRepository.findByKey("free_student_limit")).thenReturn(Optional.of(freeStudents));
        when(platformSettingRepository.findByKey("tenant_application_response_sla_hours"))
                .thenReturn(Optional.of(responseSla));

        assertThatThrownBy(() -> service.updateBatch(new PlatformSettingBatchRequest(List.of(
                new PlatformSettingChangeRequest("free_student_limit", "75"),
                new PlatformSettingChangeRequest("tenant_application_response_sla_hours", "not-hours"))), null))
                .isInstanceOf(PlatformSettingValidationException.class);

        assertThat(freeStudents.getValue()).isEqualTo("50");
        assertThat(responseSla.getValue()).isEqualTo("72");
        verify(platformSettingRepository, org.mockito.Mockito.never()).saveAllAndFlush(any());
    }

    @Test
    void listHidesDeprecatedRowsFromTheAdminCatalog() {
        when(platformSettingRepository.findAllByOrderByKeyAsc()).thenReturn(List.of(
                setting("free_student_limit", "50"),
                setting("suspension_default_days", "0")));

        assertThat(service.list()).extracting(response -> response.key())
                .containsExactly("free_student_limit");
    }

    private PlatformSetting setting(String key, String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setKey(key);
        setting.setValue(value);
        setting.setDescription("Test setting");
        return setting;
    }
}
