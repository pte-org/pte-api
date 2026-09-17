package com.pte.billing.internal.controller;

import com.pte.billing.internal.service.PayOsWebhookService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public PayOS callback; authenticity is verified inside the billing module. */
@RestController
@RequestMapping("/webhooks/payos")
public class PayOsWebhookController {

    private final PayOsWebhookService payOsWebhookService;

    public PayOsWebhookController(PayOsWebhookService payOsWebhookService) {
        this.payOsWebhookService = payOsWebhookService;
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    public ApiResponse<Void> receive(@RequestBody String rawPayload) {
        payOsWebhookService.handle(rawPayload);
        return ApiResponse.success(null);
    }
}
