package com.pte.examdelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives ExamDeliveryOutboxRelay's poll() + the outbox cleanup job
// (rabbitmq-outbox-migration Phase 5) — required for @Scheduled to run at all.
@EnableScheduling
@SpringBootApplication
public class ExamDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamDeliveryApplication.class, args);
    }
}
