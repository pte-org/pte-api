package com.pte.itembank.internal.generation.config;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Deployment-owned settings for AI question generation. Credentials have no source default; the values come
 * from the environment (see {@code application.yml}) and never appear in logs or error messages.
 */
@Validated
@ConfigurationProperties(prefix = "itembank.generation")
@Getter
@Setter
public class QuestionGenerationProperties {

    /** Master switch. Off by default: no worker, and submit answers GENERATION_DISABLED. */
    private boolean enabled;

    /** Use the deterministic stub generator (test, local and local-schema profiles only). */
    private boolean stub;

    @Valid
    private final Llm llm = new Llm();
    @Valid
    private final Kokoro kokoro = new Kokoro();
    @Valid
    private final Limits limits = new Limits();
    @Valid
    private final Worker worker = new Worker();
    @Valid
    private final Review review = new Review();

    @Getter
    @Setter
    public static class Llm {
        private String baseUrl = "";
        private String apiKey = "";
        private String generatorModel = "Capstone";
        private String reviewerModel = "Capstone";
        private double generatorTemperature = 0.8;
        private double reviewerTemperature = 0.0;
        @Min(0)
        private int maxTokens = 0;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(90);
        private Duration retryDelay = Duration.ofSeconds(2);
        /** Only for the AI overview cost estimate (USD per million tokens); 0 when the gateway does not bill. */
        @DecimalMin("0")
        private BigDecimal priceInputPerMillionUsd = new BigDecimal("0.10");
        @DecimalMin("0")
        private BigDecimal priceOutputPerMillionUsd = new BigDecimal("0.40");
    }

    /** Text-to-speech endpoint, used from the audio phases on. */
    @Getter
    @Setter
    public static class Kokoro {
        private String baseUrl = "";
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(300);
        private String voicesUs = "af_heart,af_bella,am_michael";
        private String voicesUk = "bf_emma,bm_george";
    }

    @Getter
    @Setter
    public static class Limits {
        @Min(1)
        @Max(QuestionGenerationConstants.MAX_ITEMS_PER_JOB_HARD_LIMIT)
        private int maxItemsPerJob = QuestionGenerationConstants.MAX_ITEMS_PER_JOB_HARD_LIMIT;
        @Min(1)
        private int maxItemsPerUserPerDay = 100;
        @Min(1)
        private int maxItemsPerDayGlobal = 300;
        @Min(1)
        private int maxConcurrentJobs = 3;
        /** Hard allowance of provider calls for ONE question (each call may retry once inside the gateway). */
        @Min(1)
        private int maxCallsPerItem = 6;
        /** Consecutive provider outages after which the whole job stops instead of failing every item. */
        @Min(1)
        private int consecutiveProviderFailuresToStop = 3;
    }

    @Getter
    @Setter
    public static class Worker {
        @Min(100)
        private long pollDelayMs = 3000;
        /**
         * How long a worker owns a job without renewing. It is renewed before every provider call, so it only has to
         * outlive the slowest single call (read timeout, one retry and delay), with a wide margin.
         */
        private Duration leaseDuration = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class Review {
        /** Enable vision-based review of generated charts (phase 7, optional). */
        private boolean visionCheck = false;
    }
}
