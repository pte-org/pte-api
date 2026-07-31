package com.pte.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives SchedulingOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 4) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class SchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulingApplication.class, args);
    }
}
