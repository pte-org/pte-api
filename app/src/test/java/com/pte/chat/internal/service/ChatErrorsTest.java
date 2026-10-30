package com.pte.chat.internal.service;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ChatErrors#describe} hands logs exception type names and the SQLState, and nothing else. */
class ChatErrorsTest {

    private static final String MARKER = "Failing row contains (SECRET-QUESTION-TEXT)";

    @Test
    void aWrappedSqlExceptionGivesTheTypeNamesAndTheSqlStateOnly() {
        SQLException sql = new SQLException("ERROR: null value in column \"answer\" " + MARKER, "23502");
        DataIntegrityViolationException error =
                new DataIntegrityViolationException("could not execute statement; " + MARKER, sql);

        String described = ChatErrors.describe(error);

        assertThat(described).contains("sqlState=23502")
                .contains("DataIntegrityViolationException")
                .contains("SQLException")
                .doesNotContain("SECRET-QUESTION-TEXT")
                .doesNotContain("Failing row")
                .doesNotContain("null value");
    }

    @Test
    void aPostgresExceptionGivesItsSqlStateAndNotItsServerMessage() {
        PSQLException psql = new PSQLException("ERROR: value too long " + MARKER, PSQLState.DATA_ERROR);
        DataIntegrityViolationException error = new DataIntegrityViolationException("wrapped " + MARKER, psql);

        String described = ChatErrors.describe(error);

        assertThat(described).contains("sqlState=" + PSQLState.DATA_ERROR.getState())
                .contains("PSQLException")
                .doesNotContain("SECRET-QUESTION-TEXT");
    }

    @Test
    void theSqlStateIsFoundEvenWhenTheSqlExceptionIsNotTheRootCause() {
        SQLException sql = new SQLException("db " + MARKER, "22001", new IllegalStateException("root " + MARKER));

        String described = ChatErrors.describe(new RuntimeException("outer " + MARKER, sql));

        assertThat(described).contains("sqlState=22001").contains("IllegalStateException")
                .doesNotContain("SECRET-QUESTION-TEXT");
    }

    @Test
    void aSqlExceptionAtTheTopGivesItsOwnState() {
        String described = ChatErrors.describe(new SQLException("top " + MARKER, "40001"));

        assertThat(described).startsWith("SQLException").contains("sqlState=40001")
                .doesNotContain("SECRET-QUESTION-TEXT");
    }

    @Test
    void aSqlExceptionWithoutAStateAddsNoStateText() {
        String described = ChatErrors.describe(new RuntimeException("x", new SQLException("no state " + MARKER)));

        assertThat(described).doesNotContain("sqlState").doesNotContain("SECRET-QUESTION-TEXT");
    }

    @Test
    void aPlainExceptionGivesNoMessageText() {
        String described = ChatErrors.describe(new IllegalStateException("secret"));

        assertThat(described).isEqualTo("IllegalStateException");
        assertThat(described).doesNotContain("secret");
    }

    @Test
    void aPlainExceptionWithCausesNamesOnlyTheTopAndTheRootType() {
        Throwable root = new IllegalArgumentException("root secret");
        Throwable middle = new IllegalStateException("middle secret", root);
        Throwable top = new RuntimeException("top secret", middle);

        String described = ChatErrors.describe(top);

        assertThat(described).contains("RuntimeException").contains("IllegalArgumentException")
                .doesNotContain("secret");
    }

    @Test
    void anExceptionWithoutACauseIsJustItsTypeName() {
        assertThat(ChatErrors.describe(new UnsupportedOperationException())).isEqualTo("UnsupportedOperationException");
    }

    @Test
    void aCauseChainThatLoopsBackDoesNotLoopForever() throws InterruptedException {
        Exception first = new Exception("first " + MARKER);
        Exception second = new Exception("second " + MARKER);
        first.initCause(second);
        second.initCause(first);
        AtomicReference<String> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(ChatErrors.describe(first)), "chat-errors-cycle");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);

        worker.start();
        worker.join(3_000);

        assertThat(worker.isAlive()).as("describe() returned for a cyclic cause chain").isFalse();
        assertThat(result.get()).isNotNull().doesNotContain("SECRET-QUESTION-TEXT");
    }

    @Test
    void aLongCauseChainIsHandled() {
        Throwable current = new IllegalArgumentException("deep");
        for (int i = 0; i < 200; i++) {
            current = new RuntimeException("level " + i + " " + MARKER, current);
        }

        assertThat(ChatErrors.describe(current)).contains("IllegalArgumentException")
                .doesNotContain("SECRET-QUESTION-TEXT");
    }
}
