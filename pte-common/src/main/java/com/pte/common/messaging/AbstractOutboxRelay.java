package com.pte.common.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Polling outbox relay: replaces Debezium's WAL-tail CDC with an in-process
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} poller + RabbitMQ publisher
 * confirms (ADR-002 supersession, `rabbitmq-outbox-migration` plan). A service
 * subclass supplies only {@link #publish}, its {@link OutboxJpaRepository},
 * and a {@link RabbitTemplate} — batching, locking, retry and quarantine all
 * live here.
 *
 * <p><b>Do NOT add ShedLock here.</b> {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * already gives correct per-row mutual exclusion across multiple instances of
 * the same service — every instance can poll concurrently and safely, each
 * claiming a disjoint set of rows. ShedLock solves a different problem (only
 * one instance may run a job body at all) and would wrongly serialize the
 * whole poll to a single instance, defeating horizontal scaling of the relay.
 * Reach for {@code SKIP LOCKED} out of habit, not ShedLock, if this class ever
 * needs revisiting.
 *
 * <p>Each row is claimed and published in its own transaction, one row at a
 * time (see {@link #claimAndProcessOne()}) — the claim query's pessimistic
 * lock, the publish-and-confirm call, and the {@code published} flip all
 * happen on the SAME connection inside ONE transaction, so the lock is held
 * for exactly the row's own processing window and released on commit. This
 * transaction is demarcated programmatically via {@link TransactionTemplate}
 * with {@code PROPAGATION_REQUIRES_NEW} rather than an {@code @Transactional}
 * method on this class, specifically to avoid the classic Spring AOP
 * self-invocation pitfall (a plain {@code this.foo()} call from {@code poll()}
 * to an {@code @Transactional} sibling method on the same instance silently
 * bypasses the proxy and runs with no transaction at all). One row's publish
 * failure therefore rolls back (or, once quarantined, commits) only that
 * row's own transaction — an already-broker-confirmed sibling claimed earlier
 * in the same poll cycle is never affected.
 *
 * <p>Fetching one row per transaction (rather than claiming a whole batch
 * up front and iterating with a shared lock) is deliberate: holding a
 * pessimistic lock across an outer transaction while flipping it via a
 * separate {@code REQUIRES_NEW} connection would have that inner UPDATE block
 * forever on the outer transaction's own still-held lock (a same-process,
 * cross-connection self-deadlock). Claiming one row per short transaction
 * sidesteps this entirely.
 *
 * @param <T> the service's concrete outbox entity
 */
@Slf4j
public abstract class AbstractOutboxRelay<T extends AbstractOutboxEntry> {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final OutboxJpaRepository<T> repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    @Value("${pte.outbox.batch-size:100}")
    private int batchSize;

    @Value("${pte.outbox.max-publish-attempts:10}")
    private int maxPublishAttempts;

    @Value("${pte.outbox.confirm-timeout-ms:5000}")
    private long confirmTimeoutMs;

    protected AbstractOutboxRelay(OutboxJpaRepository<T> repository, RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setName(getClass().getSimpleName() + "-outboxRelay");
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, definition);
    }

    /**
     * Map this row to its RabbitMQ destination and publish it, via
     * {@link #publishAndConfirm} — only exchange/routing-key routing
     * decisions belong here, never business logic.
     */
    protected abstract void publish(T entry);

    /**
     * Send {@code entry.getPayload()} (never the entity itself — {@code
     * publishAttempts}/{@code lastError} are internal bookkeeping and must
     * never leak into a message body a consumer receives) to {@code
     * exchange}/{@code routingKey} and block until the broker confirms
     * receipt. {@code eventId}/{@code eventType} travel as AMQP message
     * properties ({@code messageId}, a custom {@code eventType} header) —
     * the RabbitMQ replacement for Debezium's Kafka-header dedup contract;
     * consumers read {@code Message.getMessageProperties()} instead of
     * {@code ConsumerRecord.headers()}. Requires
     * {@code spring.rabbitmq.publisher-confirm-type=correlated},
     * {@code publisher-returns=true} and {@code template.mandatory=true} on
     * the caller's {@code RabbitTemplate}. Throws if the broker nacks,
     * returns the message undeliverable, or the confirm wait times out — the
     * caller ({@link #claimAndProcessOne()}) handles retry/quarantine
     * bookkeeping.
     */
    protected final void publishAndConfirm(T entry, String exchange, String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(entry.getId().toString());
        properties.setHeader("eventType", entry.getEventType());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(entry.getPayload().getBytes(StandardCharsets.UTF_8), properties);
        rabbitTemplate.invoke(operations -> {
            operations.send(exchange, routingKey, message);
            operations.waitForConfirmsOrDie(confirmTimeoutMs);
            return null;
        });
    }

    @Scheduled(fixedDelayString = "${pte.outbox.poll-interval-ms:2000}")
    public void poll() {
        for (int processed = 0; processed < batchSize; processed++) {
            if (!claimAndProcessOne()) {
                break;
            }
        }
    }

    /**
     * Claim exactly one unpublished, non-quarantined row and publish it, all
     * within a single {@code REQUIRES_NEW} transaction. Returns {@code false}
     * when there is nothing left to claim this cycle (poll() stops the
     * batch loop early in that case rather than issuing empty queries).
     */
    private boolean claimAndProcessOne() {
        return Boolean.TRUE.equals(requiresNewTransactionTemplate.execute(status -> {
            Pageable oneRow = PageRequest.of(0, 1);
            List<T> claimed = repository.findClaimableBatch(maxPublishAttempts, oneRow);
            if (claimed.isEmpty()) {
                return false;
            }
            T entry = claimed.get(0);
            try {
                publish(entry);
                entry.setPublished(true);
                entry.setPublishedAt(Instant.now());
            } catch (RuntimeException ex) {
                recordFailure(entry, ex);
            }
            repository.save(entry);
            return true;
        }));
    }

    private void recordFailure(T entry, RuntimeException ex) {
        int attempts = entry.getPublishAttempts() + 1;
        entry.setPublishAttempts(attempts);
        entry.setLastError(truncate(ex.getMessage()));
        if (attempts >= maxPublishAttempts) {
            entry.setQuarantined(true);
            log.error("Outbox row {} (eventType={}) quarantined after {} failed publish attempts: {}",
                    entry.getId(), entry.getEventType(), attempts, ex.getMessage());
        } else {
            log.warn("Outbox row {} (eventType={}) publish attempt {} failed: {}",
                    entry.getId(), entry.getEventType(), attempts, ex.getMessage());
        }
        // No re-throw: this REQUIRES_NEW transaction commits the failure
        // bookkeeping instead of rolling back, so the poll() loop's next
        // iteration (and any already-confirmed sibling from a prior
        // iteration) is unaffected by this row's failure.
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }
}
