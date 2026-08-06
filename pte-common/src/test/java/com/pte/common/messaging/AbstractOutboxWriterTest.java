package com.pte.common.messaging;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AbstractOutboxWriter}'s serialize-then-persist contract,
 * written ahead of the Jackson 2 -> Jackson 3 migration (jackson3-objectmapper-migration
 * Phase 1). Exercises the injected {@link JsonMapper} directly rather than mocking it,
 * since the behavior under test IS the (de)serialization contract.
 */
class AbstractOutboxWriterTest {

    /** Minimal concrete outbox entity, only needed to satisfy the generic bound. */
    static class TestOutboxEntry extends AbstractOutboxEntry {
    }

    static class TestOutboxWriter extends AbstractOutboxWriter<TestOutboxEntry> {
        TestOutboxEntry persisted;

        TestOutboxWriter(JsonMapper jsonMapper) {
            super(jsonMapper);
        }

        @Override
        protected TestOutboxEntry instantiate() {
            return new TestOutboxEntry();
        }

        @Override
        protected void persist(TestOutboxEntry entry) {
            this.persisted = entry;
        }
    }

    record SamplePayload(String name, int value) {
    }

    /** A payload whose getter always fails serialization, to exercise the error path. */
    static class BrokenPayload {
        public String getBoom() {
            throw new RuntimeException("boom");
        }
    }

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void write_serializesPayloadAndPersistsEntry() {
        TestOutboxWriter writer = new TestOutboxWriter(jsonMapper);
        UUID tenantId = UUID.randomUUID();

        writer.write("Tenant", "agg-1", "TenantCreated", new SamplePayload("acme", 42), tenantId);

        assertThat(writer.persisted).isNotNull();
        assertThat(writer.persisted.getAggregateType()).isEqualTo("Tenant");
        assertThat(writer.persisted.getAggregateId()).isEqualTo("agg-1");
        assertThat(writer.persisted.getEventType()).isEqualTo("TenantCreated");
        assertThat(writer.persisted.getTenantId()).isEqualTo(tenantId);
        assertThat(writer.persisted.getOccurredAt()).isNotNull();
        assertThat(writer.persisted.getPayload())
                .contains("\"name\":\"acme\"")
                .contains("\"value\":42");
    }

    @Test
    void write_nullPayloadFields_stillPersistsWithNullJsonFields() {
        TestOutboxWriter writer = new TestOutboxWriter(jsonMapper);

        writer.write("Tenant", "agg-2", "TenantRenamed", new SamplePayload(null, 0), null);

        assertThat(writer.persisted.getPayload()).contains("\"name\":null");
        assertThat(writer.persisted.getTenantId()).isNull();
    }

    @Test
    void write_serializationFailure_wrapsAsIllegalStateExceptionWithJacksonExceptionCause() {
        TestOutboxWriter writer = new TestOutboxWriter(jsonMapper);

        assertThatThrownBy(() -> writer.write("Tenant", "agg-3", "TenantCreated", new BrokenPayload(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantCreated")
                .hasCauseInstanceOf(JacksonException.class);

        assertThat(writer.persisted).isNull();
    }
}
