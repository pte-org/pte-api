package com.pte.examdelivery.web;

import com.pte.common.exception.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Registers the shared {@link GlobalExceptionHandler} within exam-delivery's component scan. */
@RestControllerAdvice
public class ExamDeliveryExceptionHandler extends GlobalExceptionHandler {
}
