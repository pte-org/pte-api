package com.pte.notification.internal.listener;

import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.stream.Collectors;

/** Mails password reset links without retaining them in notification history. */
@Component
public class PasswordResetNotificationListener {

    private static final String SUBJECT = "PTE Prep - Đặt lại mật khẩu / Reset your password";

    private final NotificationDispatchService dispatchService;

    public PasswordResetNotificationListener(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetEmailRequestedEvent event) {
        String links = event.links().stream()
                .map(link -> "- %s: %s".formatted(link.username(), link.url()))
                .collect(Collectors.joining("\n"));
        String body = """
                Đặt lại mật khẩu

                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản PTE Prep gắn với email này.
                Mở liên kết bên dưới để chọn mật khẩu mới. Liên kết dùng được một lần và hết hạn sau 30 phút.

                %s

                Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu của bạn sẽ không thay đổi.

                ----

                Reset your password

                We received a request to reset the password of the PTE Prep account linked to this email.
                Open the link below to choose a new password. It works once and expires in 30 minutes.

                %s

                If you did not ask for this, ignore this email; your password will stay as it is.

                PTE Prep Platform Team
                """.formatted(links, links);

        dispatchService.dispatchExternalSensitive(NotificationType.PASSWORD_RESET_LINK_SENT, event.email(), null,
                "password-reset:" + event.requestId(), SUBJECT, body);
    }
}
