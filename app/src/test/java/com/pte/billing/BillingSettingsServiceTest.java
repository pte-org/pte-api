package com.pte.billing;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.setting.PlatformSettingNotFoundException;
import com.pte.billing.internal.service.PlatformSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingSettingsServiceTest {

    @Mock
    private PlatformSettingService platformSettingService;

    @Test
    void missingResponseSlaUsesMigrationSafeDefault() {
        when(platformSettingService.getInteger(BillingConstants.TENANT_APPLICATION_RESPONSE_SLA_HOURS_SETTING_KEY))
                .thenThrow(new PlatformSettingNotFoundException(
                        BillingConstants.TENANT_APPLICATION_RESPONSE_SLA_HOURS_SETTING_KEY));

        BillingSettingsService service = new BillingSettingsService(platformSettingService);

        assertThat(service.getTenantApplicationResponseSlaHours())
                .isEqualTo(BillingConstants.DEFAULT_TENANT_APPLICATION_RESPONSE_SLA_HOURS);
    }
}
