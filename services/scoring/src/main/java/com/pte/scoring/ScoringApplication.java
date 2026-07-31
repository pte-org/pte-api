package com.pte.scoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives ScoringOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 6) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class ScoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScoringApplication.class, args);
    }
}
