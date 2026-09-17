package com.pte.billing.internal.service;

import com.pte.billing.domain.PlatformSetting;
import com.pte.billing.internal.dto.request.PlatformSettingRequest;
import com.pte.billing.internal.exception.PlatformSettingInvalidException;
import com.pte.billing.internal.exception.PlatformSettingNotFoundException;
import com.pte.billing.internal.repository.PlatformSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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
        when(platformSettingRepository.saveAndFlush(any(PlatformSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.getInteger("free_student_limit")).isEqualTo(50);
        service.update("free_student_limit", new PlatformSettingRequest("75", "Updated"));

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

    private PlatformSetting setting(String key, String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setKey(key);
        setting.setValue(value);
        setting.setDescription("Test setting");
        return setting;
    }
}
