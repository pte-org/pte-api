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
import com.pte.assessment.internal.controller.ExamDefinitionController;
import com.pte.support.internal.controller.AdminSupportTicketController;
import com.pte.shared.audit.internal.controller.PlatformAuditLogController;
import com.pte.identity.internal.controller.PlatformUserDirectoryController;
import com.pte.tenancy.internal.controller.OrganizationController;
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
        assertMethod(TenantApplicationController.class, "changeStatus", MANAGER);
        // The merged controller has no class-level rule: every platform governance operation carries it per method.
        for (String operation : new String[] {"onboard", "changeStatus", "suspensionImpact",
                "updateBranding"}) {
            assertMethod(OrganizationController.class, operation, MANAGER);
        }
        assertMethod(PlanController.class, "create", MANAGER);
        assertMethod(PlanController.class, "update", MANAGER);
        assertMethod(PlanController.class, "changeStatus", MANAGER);
        assertMethod(AdminLicenseCodeController.class, null, MANAGER);
        assertMethod(PlatformCommercialController.class, null, MANAGER);
        assertMethod(AnnouncementController.class, null, MANAGER);
        assertMethod(AdminSupportTicketController.class, null, MANAGER);
        assertMethod(PlatformAuditLogController.class, null, MANAGER);
        assertMethod(PlatformUserDirectoryController.class, null, MANAGER);
    }

    @Test
    void sensitiveControllersAndActionsRemainAdminOnly() throws Exception {
        assertMethod(PlatformSettingController.class, null, ADMIN);
        assertMethod(PlatformUserController.class, null, ADMIN);
        assertMethod(OrderController.class, null, "hasRole('HOST_ADMIN')");
        assertMethod(SubscriptionController.class, null, "hasRole('HOST_ADMIN')");
        assertMethod(QuestionController.class, null,
                "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')");
        assertMethod(AdminLicenseCodeController.class, "reveal", ADMIN);
        assertMethod(AdminLicenseCodeController.class, "revokePreview", ADMIN);
        assertMethod(AdminLicenseCodeController.class, "revoke", ADMIN);
        assertMethod(LicenseCodeController.class, "listLegacy", ADMIN);
        assertMethod(OrganizationController.class, "grantQuota", ADMIN);
        assertMethod(OrganizationController.class, "quotaHistory", ADMIN);
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
