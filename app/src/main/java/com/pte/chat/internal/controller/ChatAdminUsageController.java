package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.service.report.ChatUsageDay;
import com.pte.chat.internal.service.report.ChatUsageReportService;
import com.pte.shared.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Chat cost and traffic by day for platform admins; counts only, never anything a student wrote. */
@RestController
@RequestMapping(ChatConstants.ADMIN_USAGE_PATH)
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatAdminUsageController {

    private final ChatUsageReportService reportService;

    public ChatAdminUsageController(ChatUsageReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<List<ChatUsageDay>> usage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(reportService.report(from, to));
    }
}
