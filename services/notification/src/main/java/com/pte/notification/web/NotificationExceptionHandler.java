package com.pte.notification.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within notification's component scan. */
@RestControllerAdvice
public class NotificationExceptionHandler extends GlobalExceptionHandler {
}
