package com.pte.notification.internal.listener;

import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.internal.service.InboxAppendService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Notification-owned events only; later business listeners consume their own source-module events. */
@Component
public class InboxRequestListener {
    private final InboxAppendService appender;
    public InboxRequestListener(InboxAppendService appender) { this.appender = appender; }
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onRequested(InboxNotificationRequested event) { appender.append(event); }
}
