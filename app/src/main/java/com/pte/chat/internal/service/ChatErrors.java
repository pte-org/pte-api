package com.pte.chat.internal.service;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A log-safe description of a failure. A database error message can quote the failing row, which holds the
 * student's question and the answer, so logs get only the exception types and the SQLState.
 */
public final class ChatErrors {

    private ChatErrors() {
    }

    public static String describe(Throwable error) {
        StringBuilder text = new StringBuilder(error.getClass().getSimpleName());
        // A cause chain can legally loop back on itself, so each throwable is visited once.
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = error;
        String sqlState = null;
        for (Throwable current = error; current != null && visited.add(current); current = current.getCause()) {
            root = current;
            if (sqlState == null && current instanceof SQLException sql) {
                sqlState = sql.getSQLState();
            }
        }
        if (root != error) {
            text.append(" caused by ").append(root.getClass().getSimpleName());
        }
        if (sqlState != null) {
            text.append(" sqlState=").append(sqlState);
        }
        return text.toString();
    }
}
