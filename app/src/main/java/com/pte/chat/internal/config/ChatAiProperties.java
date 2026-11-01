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
    private int readTimeoutMs = 30_000;
    private int dailyLimit = 20;
    private int maxInputChars = 1_000;
    private int historySize = 6;
    private int maxConcurrent = 20;
    /** Used only for the admin cost estimate; update when the provider changes the price of the pinned model. */
    private BigDecimal priceInputPerMillionUsd = new BigDecimal("0.10");
    private BigDecimal priceOutputPerMillionUsd = new BigDecimal("0.40");
}
