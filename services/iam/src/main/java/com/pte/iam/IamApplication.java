package com.pte.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives IamOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 3) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
