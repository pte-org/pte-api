package com.pte.itembank.internal.generation.config;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
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

    /** Present only when generation is on and the stub is not: the real 9router transport. */
    @Bean
    @ConditionalOnExpression("${itembank.generation.enabled:false} and !${itembank.generation.stub:false}")
    public NineRouterLlmGateway nineRouterLlmGateway(QuestionGenerationProperties properties, JsonMapper jsonMapper,
                                                     GenerationUsageRecorder usageRecorder) {
        QuestionGenerationProperties.Llm llm = properties.getLlm();
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
        return new NineRouterLlmGateway(restClient, properties, jsonMapper, usageRecorder);
    }

    /** A dedicated single thread: a job runs for minutes and must not block the shared one-thread scheduler. */
    @Bean(name = QuestionGenerationConstants.EXECUTOR_NAME, destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "itembank.generation", name = "enabled", havingValue = "true")
    ThreadPoolTaskExecutor questionGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(QuestionGenerationConstants.EXECUTOR_THREAD_PREFIX);
        return executor;
    }
}
