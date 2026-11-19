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
    void anInvalidStoredGenerationLimitFallsBackToTheDeploymentValue() {
        when(platformSettingService.getInteger(QuestionGenerationLimit.ITEMS_PER_DAY.key()))
                .thenThrow(new com.pte.billing.internal.exception.setting.PlatformSettingInvalidException(
                        QuestionGenerationLimit.ITEMS_PER_DAY.key()));

        BillingSettingsService service = new BillingSettingsService(platformSettingService);

        assertThat(service.questionGenerationLimit(QuestionGenerationLimit.ITEMS_PER_DAY, 300)).isEqualTo(300);
    }

    @Test
    void theThreeUnlimitableLimitsAcceptZeroInTheSettingsRange() {
        assertThat(QuestionGenerationLimit.ITEMS_PER_BATCH.minimum()).isZero();
        assertThat(QuestionGenerationLimit.ITEMS_PER_USER_PER_DAY.minimum()).isZero();
        assertThat(QuestionGenerationLimit.ITEMS_PER_DAY.minimum()).isZero();
        assertThat(QuestionGenerationLimit.ITEMS_PER_JOB.minimum()).isEqualTo(1);
        assertThat(QuestionGenerationLimit.CONCURRENT_JOBS.minimum()).isEqualTo(1);
    }

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
