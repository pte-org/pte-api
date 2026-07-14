package com.aptis.modules.iam.config;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.aptis.common.exception.ErrorCode;
import com.aptis.common.filter.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ErrorCode.ACCESS_DENIED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(buildResponseBody(ErrorCode.ACCESS_DENIED, request.getRequestURI()));
    }

    private String buildResponseBody(ErrorCode errorCode, String path) {
        return """
                {"timestamp":"%s","success":false,"status":%d,"code":"%s","message":"%s","path":"%s","requestId":"%s"}"""
                .formatted(
                        Instant.now(),
                        errorCode.getStatus().value(),
                        errorCode.name(),
                        errorCode.getDefaultMessage(),
                        path,
                        MDC.get(RequestIdFilter.MDC_KEY));
    }
}
