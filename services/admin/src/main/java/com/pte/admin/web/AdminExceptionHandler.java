package com.pte.admin.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within admin's component scan. */
@RestControllerAdvice
public class AdminExceptionHandler extends GlobalExceptionHandler {
}
