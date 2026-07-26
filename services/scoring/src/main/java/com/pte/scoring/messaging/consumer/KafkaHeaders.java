package com.pte.scoring.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared Debezium/Kafka header extraction for scoring's two consumers. */
final class KafkaHeaders {

    private KafkaHeaders() {
    }

    static String requireString(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            throw new IllegalStateException("Missing Kafka header '" + key + "'");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    static UUID require(ConsumerRecord<String, String> record, String key) {
        return UUID.fromString(requireString(record, key));
    }
}
