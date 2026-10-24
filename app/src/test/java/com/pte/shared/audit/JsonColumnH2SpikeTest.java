package com.pte.shared.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 spike gate. Frozen/read-whole aggregates (ExamVersion items, AnswerEvaluation details)
 * rely on {@code @JdbcTypeCode(SqlTypes.JSON)} without {@code columnDefinition}. Proves a nested
 * record list round-trips on H2 create-drop. Characterization test: it may pass on arrival; the
 * phase-1 decision (JSON vs AttributeConverter fallback) is recorded in the TDD artifact.
 */
@DataJpaTest
class JsonColumnH2SpikeTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void nestedRecordListRoundTripsThroughAJsonColumn() {
        var payload = new Payload("v1", List.of(
                new Item("item-1", List.of(new Option("A", true), new Option("B", false))),
                new Item("item-2", List.of())));
        JsonHolder holder = new JsonHolder();
        holder.payload = payload;
        entityManager.persist(holder);
        entityManager.flush();
        entityManager.clear();

        JsonHolder loaded = entityManager.find(JsonHolder.class, holder.id);

        assertThat(loaded.payload).isEqualTo(payload);
    }

    record Option(String label, boolean correct) {
    }

    record Item(String itemPublicId, List<Option> options) {
    }

    record Payload(String schema, List<Item> items) {
    }

    @Entity
    @Table(name = "json_spike_holder")
    static class JsonHolder {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        @JdbcTypeCode(SqlTypes.JSON)
        Payload payload;
    }
}
