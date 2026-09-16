package com.pte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// @EnableScheduling: reserved for the async work RabbitMQ backs post-Phase 07
// (scoring, notification) — not used by anything in Phase 01 itself.
@EnableScheduling
@SpringBootApplication
public class PteApplication {

    public static void main(String[] args) {
        // postgres-utc-timestamptz-migration Phase 1 (carried over from
        // services/admin's AdminApplication): must run before SpringApplication.run
        // constructs the DataSource — the JVM default zone drives what PgJDBC sends as
        // the connection's TimeZone parameter. Independent of host OS timezone (e.g.
        // avoids the Asia/Saigon alias postgres:17's tzdata doesn't recognize).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(PteApplication.class, args);
    }
}
