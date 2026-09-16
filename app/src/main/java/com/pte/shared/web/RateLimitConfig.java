package com.pte.shared.web;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wiring for {@link RateLimitFilter}'s token-bucket backend. A dedicated
 * {@link RedisClient}/connection, not Spring Data Redis's {@code
 * StringRedisTemplate} — Bucket4j's maintainers dropped spring-data-redis
 * support deliberately (it fought their CAS/Lua protocol), so this talks to
 * the same Redis (same host/port as {@code spring.data.redis.*}) through
 * Bucket4j's own Lettuce integration instead. String keys via a combined
 * codec (StringCodec for keys, ByteArrayCodec for the serialized bucket
 * state Bucket4j writes) so callers never hand-encode a tenant id to bytes.
 */
@Configuration
public class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return RedisClient.create(RedisURI.Builder.redis(host, port).build());
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient redisClient) {
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    // Idle tenant buckets expire out of Redis on their own instead of
    // accumulating forever: once a bucket has been untouched long enough
    // that it would have fully refilled anyway, there's nothing left to
    // preserve. See Bucket4j's ExpirationAfterWriteStrategy docs.
    @Bean
    public ProxyManager<String> rateLimitProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
    }
}
