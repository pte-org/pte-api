package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.DashboardSection;
import com.pte.shared.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs one section in isolation: a failure is logged with the request's correlation
 * id and degraded to an ERROR section, so one broken widget never blanks the page.
 * No raw error text reaches the response.
 */
@Component
public class DashboardSectionRunner {

    private static final Logger log = LoggerFactory.getLogger(DashboardSectionRunner.class);

    public <T> DashboardSection<T> run(String sectionName, Supplier<T> loader) {
        try {
            return DashboardSection.ok(loader.get());
        } catch (RuntimeException ex) {
            log.warn("Dashboard section '{}' failed (correlationId={})", sectionName,
                    MDC.get(CorrelationIdFilter.MDC_KEY), ex);
            return DashboardSection.error();
        }
    }
}
