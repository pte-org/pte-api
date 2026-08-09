package com.pte.authoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// @EnableScheduling drives AuthoringOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 2) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class AuthoringApplication {

    public static void main(String[] args) {
        // postgres-utc-timestamptz-migration Phase 1: see AdminApplication for why.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AuthoringApplication.class, args);
    }
}
