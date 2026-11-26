package com.pte.itembank.internal.generation.config;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
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

    /** Batch submit; off until every app instance runs code that understands PENDING batch children. */
    private boolean batchEnabled;

    /** AI-painted pictures (Describe Image); off while the image provider is unreliable. */
    private boolean pictureEnabled;

    @Valid
    private final Llm llm = new Llm();
    /**
     * A provider asked before {@link #llm} for generation only (an empty base URL means none). When it fails (quota
     * gone, outage) the call goes to {@link #llm}, which the student chat and scoring keep using on their own.
     */
    @Valid
    private final Llm primary = new Llm();
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
        /** The gateway's own web page, linked from the AI overview; empty means the root of the base URL's host. */
        private String webUrl = "";
        private String apiKey = "";
        private String generatorModel = "Capstone";
        private String reviewerModel = "Capstone";
        private double generatorTemperature = 0.8;
        private double reviewerTemperature = 0.0;
        @Min(0)
        private int maxTokens = 0;
        private Duration connectTimeout = Duration.ofSeconds(5);
        /**
         * Longest silence allowed while reading one answer. A free fallback model behind the gateway sends nothing
         * until its whole answer (thoughts included) is ready, which took close to a minute when measured.
         */
        private Duration readTimeout = Duration.ofSeconds(150);
        private Duration retryDelay = Duration.ofSeconds(2);
        /** Text-to-image model of the same gateway, for Describe Image pictures. */
        private String imageModel = "ag/gemini-3.1-flash-image";
        /** Paint attempts (provider failures or reviewer refusals) before a picture question becomes a chart. */
        private int imageMaxAttempts = 2;
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
        /** The speech service's web page, linked from the AI overview; empty means {@code <base URL>/web/}. */
        private String webUrl = "";
        private String apiKey = "";
        private Duration timeout = Duration.ofSeconds(300);
        /** Comma-separated catalog voice codes to leave out, for a speech server that lacks them. Empty = all. */
        private String voicesDisabled = "";
    }

    @Getter
    @Setter
    public static class Limits {
        @Min(1)
        @Max(QuestionGenerationConstants.MAX_ITEMS_PER_JOB_HARD_LIMIT)
        private int maxItemsPerJob = QuestionGenerationConstants.MAX_ITEMS_PER_JOB_HARD_LIMIT;
        /** 0 = no own limit (the hard cap still applies). */
        @Min(0)
        @Max(BatchGenerationConstants.MAX_ITEMS_PER_BATCH_HARD_LIMIT)
        private int maxItemsPerBatch = BatchGenerationConstants.DEFAULT_MAX_ITEMS_PER_BATCH;
        /** 0 = unlimited. */
        @Min(0)
        private int maxItemsPerUserPerDay = 100;
        /** 0 = unlimited. */
        @Min(0)
        private int maxItemsPerDayGlobal = 300;
        @Min(1)
        private int maxConcurrentJobs = 3;
        /** Prompt test runs one user may start in a day; each one is a real provider call. */
        @Min(1)
        private int maxPromptTestsPerUserPerDay = 20;
        /**
         * Hard allowance of provider calls for ONE question (each call may retry once inside the gateway). A refused
         * or broken draft is written again while a whole round still fits: 40 calls give 8 rounds.
         */
        @Min(1)
        private int maxCallsPerItem = 15;
        /**
         * Most provider calls one round can make (generation, a JSON retry, a rule rewrite, its JSON retry, a review).
         * Another round starts only while this many calls are left, and the allowance divided by it caps the rounds.
         */
        @Min(1)
        private int maxCallsPerRound = 5;
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
         * How long a worker owns a job without renewing. It is renewed before every question and every provider call,
         * so it only has to outlive the slowest single call plus the media work after it: two attempts of 150s read
         * timeout and the retry delay come to about 5 minutes, which leaves a wide margin.
         */
        private Duration leaseDuration = Duration.ofMinutes(15);
    }

    @Getter
    @Setter
    public static class Review {
        /** Enable vision-based review of generated charts (phase 7, optional). */
        private boolean visionCheck = false;
    }
}
