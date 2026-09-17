package com.spendly.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendly.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Returns JSON errors (matching the ApiError shape) instead of empty bodies when
 * a request is unauthenticated (401) or lacks permissions (403). Without this,
 * an anonymous request to a protected endpoint surfaces as an opaque 403.
 */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * Shared rather than hand-formatted: the body used to be a text block with
     * the message interpolated into it, which only stayed valid JSON as long as
     * every caller passed a message with no quotes or backslashes in it.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Authentication required");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", "Access denied");
    }

    public static void write(HttpServletResponse response, int status, String error, String message)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("fields", null);
        body.put("requestId", RequestIdFilter.current());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), body);
    }
}
