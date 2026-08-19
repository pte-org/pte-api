package com.pte.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// @EnableScheduling drives ReportingOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 7) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class ReportingApplication {

    public static void main(String[] args) {
        // postgres-utc-timestamptz-migration Phase 1: see AdminApplication for why.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ReportingApplication.class, args);
    }
}
