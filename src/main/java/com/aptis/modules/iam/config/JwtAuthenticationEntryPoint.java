package com.aptis.modules.iam.config;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.slf4j.MDC;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.aptis.common.exception.ErrorCode;
import com.aptis.common.filter.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(ErrorCode.UNAUTHENTICATED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(buildResponseBody(ErrorCode.UNAUTHENTICATED, request.getRequestURI()));
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
