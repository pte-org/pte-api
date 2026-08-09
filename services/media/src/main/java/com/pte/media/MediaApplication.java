package com.pte.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MediaApplication {

    public static void main(String[] args) {
        // postgres-utc-timestamptz-migration Phase 1: see AdminApplication (admin service)
        // for why — must run before SpringApplication.run constructs the DataSource.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(MediaApplication.class, args);
    }
}
