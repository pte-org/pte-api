package com.pte.billing;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.setting.PlatformSettingNotFoundException;
import com.pte.billing.internal.service.PlatformSettingService;
import com.pte.tenancy.TenantSuspensionDurationProvider;
import org.springframework.stereotype.Service;

/** Public billing boundary for business settings consumed by other modules. */
@Service
public class BillingSettingsService implements TenantSuspensionDurationProvider {

    private final PlatformSettingService platformSettingService;

    public BillingSettingsService(PlatformSettingService platformSettingService) {
        this.platformSettingService = platformSettingService;
    }

    /** Returns the SLA used in future tenant-application notifications. */
    public int getTenantApplicationResponseSlaHours() {
        try {
            return platformSettingService.getInteger(
                    BillingConstants.TENANT_APPLICATION_RESPONSE_SLA_HOURS_SETTING_KEY);
        } catch (PlatformSettingNotFoundException exception) {
            // Keep app-first rollout safe until V101 has been applied; invalid stored values still fail loudly.
            return BillingConstants.DEFAULT_TENANT_APPLICATION_RESPONSE_SLA_HOURS;
        }
    }

    @Override
    public int defaultSuspensionDays() {
        try {
            return platformSettingService.getInteger(BillingConstants.SUSPENSION_DEFAULT_DAYS_SETTING_KEY);
        } catch (PlatformSettingNotFoundException exception) {
            return 0;
        }
    }
}
