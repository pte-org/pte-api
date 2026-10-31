package com.pte.identity.internal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The caller is answered before any reset work runs, and that work can never fail back into the request. */
@ExtendWith(MockitoExtension.class)
class PasswordResetDispatcherTest {

    @Mock
    private PasswordResetService passwordResetService;

    private final List<Runnable> queued = new ArrayList<>();
    private final Executor deferred = queued::add;
    private PasswordResetDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new PasswordResetDispatcher(passwordResetService, deferred);
    }

    @Test
    void requestAsync_returnsBeforeTheServiceRuns_thenRunsItWithTheSameArguments() {
        dispatcher.requestAsync("host@x.vn", "198.51.100.7");

        assertThat(queued).hasSize(1);
        verifyNoInteractions(passwordResetService);

        queued.get(0).run();

        verify(passwordResetService).request("host@x.vn", "198.51.100.7");
    }

    @Test
    void requestAsync_swallowsAServiceFailure() {
        doThrow(new IllegalStateException("redis down")).when(passwordResetService)
                .request("host@x.vn", "198.51.100.7");

        dispatcher.requestAsync("host@x.vn", "198.51.100.7");

        assertThatCode(() -> queued.get(0).run()).doesNotThrowAnyException();
    }
}
