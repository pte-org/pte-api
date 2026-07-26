package com.pte.proctor.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within proctor's component scan (REST surface only — STOMP errors go through {@code ProctorStompController}'s own handlers). */
@RestControllerAdvice
public class ProctorExceptionHandler extends GlobalExceptionHandler {
}
