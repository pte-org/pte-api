package com.pte.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives AdminOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 2) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
