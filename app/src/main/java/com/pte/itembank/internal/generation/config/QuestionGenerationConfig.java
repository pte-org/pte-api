package com.pte.itembank.internal.generation.config;

import com.pte.billing.QuestionGenerationLimit;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.llm.FallbackLlmGateway;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.NineRouterLlmGateway;
import com.pte.itembank.internal.generation.usage.GenerationUsageRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(QuestionGenerationProperties.class)
public class QuestionGenerationConfig {

    private static final String BEARER = "Bearer ";
    private static final String TRAILING_SLASHES = "/+$";

    @Bean
    public QuestionGenerationPropertiesValidator questionGenerationPropertiesValidator(
            QuestionGenerationProperties properties, Environment environment) {
        return new QuestionGenerationPropertiesValidator(properties, environment);
    }

    /**
     * Present only when generation is on and the stub is not: the real transport. With a primary provider set, it is
     * asked first and the usual gateway (9router) answers when it fails.
     */
    @Bean
    @ConditionalOnExpression("${itembank.generation.enabled:false} and !${itembank.generation.stub:false}")
    public LlmGateway nineRouterLlmGateway(QuestionGenerationProperties properties, JsonMapper jsonMapper,
                                           GenerationUsageRecorder usageRecorder) {
        LlmGateway usual = gatewayFor(properties.getLlm(), jsonMapper, usageRecorder);
        QuestionGenerationProperties.Llm primary = properties.getPrimary();
        if (primary.getBaseUrl() == null || primary.getBaseUrl().isBlank()) {
            return usual;
        }
        return new FallbackLlmGateway(gatewayFor(primary, jsonMapper, usageRecorder), usual);
    }

    private static NineRouterLlmGateway gatewayFor(QuestionGenerationProperties.Llm llm, JsonMapper jsonMapper,
                                                   GenerationUsageRecorder usageRecorder) {
        // Redirects are never followed: a 3xx must not carry the bearer key to another host.
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(llm.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(llm.getReadTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(llm.getBaseUrl().trim().replaceAll(TRAILING_SLASHES, ""))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, BEARER + llm.getApiKey())
                .build();
        return new NineRouterLlmGateway(restClient, llm, jsonMapper, usageRecorder);
    }

    /**
     * Dedicated threads, so a job that runs for minutes never blocks the shared scheduler. Sized for the highest
     * parallel-run setting; the setting in force (checked when a job is claimed) decides how many actually run, and
     * idle threads time out.
     */
    @Bean(name = QuestionGenerationConstants.EXECUTOR_NAME, destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "itembank.generation", name = "enabled", havingValue = "true")
    ThreadPoolTaskExecutor questionGenerationExecutor() {
        int threads = QuestionGenerationLimit.CONCURRENT_JOBS.maximum();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(QuestionGenerationConstants.EXECUTOR_THREAD_PREFIX);
        return executor;
    }
}
