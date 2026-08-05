package com.pte.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// @EnableScheduling drives AdminOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 2) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        // postgres-utc-timestamptz-migration Phase 1: must run before SpringApplication.run
        // constructs the DataSource — the JVM default zone drives what PgJDBC sends as the
        // connection's TimeZone parameter. Independent of host OS timezone (e.g. avoids the
        // Asia/Saigon alias postgres:17's tzdata doesn't recognize).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AdminApplication.class, args);
    }
}
