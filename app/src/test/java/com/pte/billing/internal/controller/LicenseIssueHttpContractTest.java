package com.pte.billing.internal.controller;

import com.pte.billing.internal.service.LicenseCodeService;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.dto.response.AdminLicenseCodeSummary;
import com.pte.billing.internal.dto.response.LicenseCodeRevealResponse;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.web.LicenseCodeRevealNoStoreFilter;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MVC verifies request/response contracts; JWT filters are outside this fixture. */
class LicenseIssueHttpContractTest {
    private final LicenseCodeService service = mock(LicenseCodeService.class);
    private final CurrentUser actor = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private final UUID plan = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context = mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(actor);
        mvc = MockMvcBuilders.standaloneSetup(new AdminLicenseCodeController(service), new LicenseCodeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new LicenseCodeRevealNoStoreFilter()).build();
    }
    @AfterEach void cleanup() { context.close(); }

    @Test void bothRoutesAcceptExpiredPayloadOnReplayAndNeverReturnBearerCode() throws Exception {
        Instant expired = Instant.parse("2020-01-01T00:00:00Z");
        when(service.issue(plan, expired, key, actor)).thenReturn(new LicenseIssueReceipt(UUID.randomUUID(), plan,
                "ISSUED", "EXPIRED", Instant.EPOCH, expired, true));
        for (String route : new String[]{"/api/v1/admin/license-codes", "/api/v1/license-codes"}) {
            mvc.perform(post(route).header("Idempotency-Key", key).contentType("application/json")
                    .content("{\"planId\":\"" + plan + "\",\"codeExpiresAt\":\"2020-01-01T00:00:00Z\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true))
                    .andExpect(jsonPath("$.data.code").doesNotExist());
        }
    }
    @Test void bothRoutesRejectMissingAndMalformedKeys() throws Exception {
        for (String route : new String[]{"/api/v1/admin/license-codes", "/api/v1/license-codes"}) {
            var request = post(route).contentType("application/json").content("{\"planId\":\"" + plan + "\"}");
            mvc.perform(request).andExpect(status().isBadRequest());
            mvc.perform(post(route).header("Idempotency-Key", "invalid").contentType("application/json")
                    .content("{\"planId\":\"" + plan + "\"}")).andExpect(status().isBadRequest());
            mvc.perform(post(route).header("Idempotency-Key", "1-1-1-1-1").contentType("application/json")
                    .content("{\"planId\":\"" + plan + "\"}")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void firstIssueIs201AndEquivalentOffsetParsesToSameInstant() throws Exception {
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        when(service.issue(plan, expiry, key, actor)).thenReturn(new LicenseIssueReceipt(UUID.randomUUID(), plan,
                "ISSUED", "ISSUED", Instant.EPOCH, expiry, false));
        mvc.perform(post("/api/v1/admin/license-codes").header("Idempotency-Key", key).contentType("application/json")
                .content("{\"planId\":\"" + plan + "\",\"codeExpiresAt\":\"2030-01-01T07:00:00+07:00\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").doesNotExist());
    }

    @Test void revokePreviewUsesPublicIdAndLegacyTokenRouteFailsClosed() throws Exception {
        UUID publicId = UUID.randomUUID();
        when(service.previewRevoke(publicId, actor)).thenReturn(new LicenseRevokePreviewResponse(
                publicId, plan, "REDEEMED", "EXAM_SUBSCRIPTION", UUID.randomUUID(), "ACTIVE",
                UUID.randomUUID(), 2, 1, 3, List.of(UUID.randomUUID(), UUID.randomUUID()),
                Instant.parse("2030-01-01T00:05:00Z"), "digest"));

        mvc.perform(get("/api/v1/admin/license-codes/" + publicId + "/revoke-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId").value(publicId.toString()))
                .andExpect(jsonPath("$.data.scheduledCount").value(2))
                .andExpect(jsonPath("$.data.scopeDigest").value("digest"));

        doThrow(new LicenseCodeException(org.springframework.http.HttpStatus.CONFLICT,
                BillingConstants.LICENSE_CODE_REVOKE_LEGACY_ENDPOINT))
                .when(service).rejectLegacyRevoke(actor);
        mvc.perform(post("/api/v1/license-codes/RAW-BEARER/revoke")
                        .contentType("application/json")
                        .content("{\"reason\":\"manual\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("RAW-BEARER"))));
        verify(service).rejectLegacyRevoke(actor);
    }

    @Test void adminReadsUseMaskedPageAndLegacyRawListIsRetired() throws Exception {
        UUID publicId = UUID.randomUUID();
        AdminLicenseCodeSummary row = new AdminLicenseCodeSummary(publicId, "•••• STUV", "ISSUED", "ISSUED",
                plan, "Exam", "EXAM_PACKAGE", Instant.EPOCH, null, null, null, null);
        when(service.listForAdmin(null, null, null, null, null, actor))
                .thenReturn(new PagedResult<>(List.of(row), new PageMeta(0, 25, 1, 1, true, true, false, false)));

        mvc.perform(get("/api/v1/admin/license-codes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.data[0].maskedCode").value("•••• STUV"))
                .andExpect(jsonPath("$.data.data[0].code").doesNotExist())
                .andExpect(jsonPath("$.data.meta.totalElements").value(1));

        mvc.perform(get("/api/v1/license-codes"))
                .andExpect(status().isGone())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("STUV"))));
        verify(service).ensureLegacyListAccess(actor);
    }

    @Test void lookupBindsBearerInPostBodyAndRevealIsNoStore() throws Exception {
        UUID publicId = UUID.randomUUID();
        AdminLicenseCodeSummary row = new AdminLicenseCodeSummary(publicId, "•••• STUV", "ISSUED", "ISSUED",
                plan, "Exam", "EXAM_PACKAGE", Instant.EPOCH, null, null, null, null);
        when(service.lookupForAdmin("abcd-efgh-jklm-npqr-stuv", actor)).thenReturn(row);
        when(service.revealForAdmin(publicId, actor)).thenReturn(new LicenseCodeRevealResponse("ABCD-EFGH-JKLM-NPQR-STUV"));

        mvc.perform(post("/api/v1/admin/license-codes/lookup")
                        .contentType("application/json")
                        .content("{\"code\":\"abcd-efgh-jklm-npqr-stuv\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedCode").value("•••• STUV"))
                .andExpect(jsonPath("$.data.code").doesNotExist());

        mvc.perform(post("/api/v1/admin/license-codes/" + publicId + "/reveal"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.data.code").value("ABCD-EFGH-JKLM-NPQR-STUV"));
        verify(service).lookupForAdmin("abcd-efgh-jklm-npqr-stuv", actor);
    }

    @Test void revealErrorsAreAlsoNoStoreAndDoNotEchoBearer() throws Exception {
        UUID publicId = UUID.randomUUID();
        doThrow(new LicenseCodeException(org.springframework.http.HttpStatus.NOT_FOUND,
                BillingConstants.LICENSE_CODE_NOT_FOUND))
                .when(service).revealForAdmin(publicId, actor);

        mvc.perform(post("/api/v1/admin/license-codes/" + publicId + "/reveal"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("RAW-BEARER"))));
    }

    @Test void revealResponseToStringRedactsBearer() {
        org.junit.jupiter.api.Assertions.assertFalse(
                new LicenseCodeRevealResponse("RAW-BEARER").toString().contains("RAW-BEARER"));
    }
}
