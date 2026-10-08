package com.pte.billing.internal.controller;

import com.pte.billing.internal.controller.AdminLicenseCodeController;
import com.pte.billing.internal.controller.LicenseCodeController;
import com.pte.billing.internal.controller.OrderController;
import com.pte.billing.internal.controller.PlatformCommercialController;
import com.pte.billing.internal.controller.PlatformSettingController;
import com.pte.billing.internal.controller.SubscriptionController;
import com.pte.assessment.internal.controller.BlueprintController;
import com.pte.identity.internal.controller.PlatformUserController;
import com.pte.itembank.internal.controller.QuestionController;
import com.pte.notification.internal.controller.AnnouncementController;
import com.pte.scoretemplate.internal.controller.ScoreTemplateController;
import com.pte.support.internal.controller.AdminSupportTicketController;
import com.pte.tenancy.internal.controller.TenantController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformManagerControllerSecurityTest {

    private static final String MANAGER = "hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')";
    private static final String ADMIN = "hasRole('PLATFORM_ADMIN')";

    @Test
    void operationsControllersExposeTheDelegatedManagerSurface() throws Exception {
        assertMethod(TenantApplicationController.class, "list", MANAGER);
        assertMethod(TenantApplicationController.class, "get", MANAGER);
        assertMethod(TenantApplicationController.class, "approve", MANAGER);
        assertMethod(TenantController.class, null, MANAGER);
        assertMethod(PlanController.class, "create", MANAGER);
        assertMethod(PlanController.class, "update", MANAGER);
        assertMethod(PlanController.class, "activate", MANAGER);
        assertMethod(AdminLicenseCodeController.class, null, MANAGER);
        assertMethod(PlatformCommercialController.class, null, MANAGER);
        assertMethod(AnnouncementController.class, null, MANAGER);
        assertMethod(AdminSupportTicketController.class, null, MANAGER);
    }

    @Test
    void sensitiveControllersAndActionsRemainAdminOnly() throws Exception {
        assertMethod(PlatformSettingController.class, null, ADMIN);
        assertMethod(PlatformUserController.class, null, ADMIN);
        assertMethod(OrderController.class, null, "hasRole('HOST_ADMIN')");
        assertMethod(SubscriptionController.class, null, "hasRole('HOST_ADMIN')");
        assertMethod(QuestionController.class, null,
                "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')");
        assertMethod(ScoreTemplateController.class, "approve",
                "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER')");
        assertMethod(BlueprintController.class, "approve",
                "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER')");
        assertMethod(AdminLicenseCodeController.class, "reveal", ADMIN);
        assertMethod(AdminLicenseCodeController.class, "revokePreview", ADMIN);
        assertMethod(AdminLicenseCodeController.class, "revoke", ADMIN);
        assertMethod(LicenseCodeController.class, "listLegacy", ADMIN);
        assertMethod(LicenseCodeController.class, "revokeLegacy", ADMIN);
        assertMethod(TenantController.class, "grantQuota", ADMIN);
        assertMethod(TenantController.class, "quotaHistory", ADMIN);
    }

    private void assertMethod(Class<?> type, String methodName, String expected) throws Exception {
        PreAuthorize annotation;
        if (methodName == null) {
            annotation = type.getAnnotation(PreAuthorize.class);
        } else {
            Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            annotation = method.getAnnotation(PreAuthorize.class);
        }
        assertThat(annotation).as("authorization for %s.%s", type.getSimpleName(), methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
