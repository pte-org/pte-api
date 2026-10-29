package com.pte.chat.internal.config;

import com.pte.chat.internal.constant.ChatConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Deployment-owned settings for the student chat. Kept apart from {@code scoring.ai.*} on purpose: its own
 * key and a pinned model, so chat rate limits can never starve answer scoring. Nothing here is checked
 * while {@code enabled} is false, see {@link ChatAiPropertiesValidator}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = ChatConstants.PROPERTY_PREFIX)
public class ChatAiProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String model = "";
    /** The provider's address. Google's by default; a proxy or a stand-in server for staging and end-to-end checks. */
    private String baseUrl = ChatConstants.DEFAULT_BASE_URL;
    private double temperature = 0.2;
    private int maxTokens = 800;
    private int connectTimeoutMs = 2_000;
    private int readTimeoutMs = 20_000;
    private int dailyLimit = 20;
    private int maxInputChars = 1_000;
    private int historySize = 6;
    private int maxConcurrent = 20;
    /** Cached answers cost no quota, so each student gets a daily cap; past it a question is charged as usual. */
    private int maxCacheHitsPerDay = 100;
    /** Longest question allowed into the shared answer cache; a longer one is probably pasted personal text. */
    private int cacheMaxQuestionChars = ChatConstants.DEFAULT_CACHE_MAX_QUESTION_CHARS;
    /** Used only for the admin cost estimate; update when the provider changes the price of the pinned model. */
    private BigDecimal priceInputPerMillionUsd = new BigDecimal("0.10");
    private BigDecimal priceOutputPerMillionUsd = new BigDecimal("0.40");
    /** Question and answer text older than this many months is blanked; cost columns stay for reports. */
    private int retentionContentMonths = ChatConstants.DEFAULT_RETENTION_CONTENT_MONTHS;
    /** A cached answer is served for this many days, then deleted and replaced by the next fresh answer. */
    private int retentionCacheDays = ChatConstants.DEFAULT_RETENTION_CACHE_DAYS;
    /** Rows blanked per statement, so the nightly job never holds a long lock. */
    private int retentionBatchSize = ChatConstants.DEFAULT_RETENTION_BATCH_SIZE;
}
