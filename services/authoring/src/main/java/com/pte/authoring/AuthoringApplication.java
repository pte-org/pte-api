package com.pte.authoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives AuthoringOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 2) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class AuthoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthoringApplication.class, args);
    }
}
