package com.pte.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives ReportingOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 7) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class ReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingApplication.class, args);
    }
}
