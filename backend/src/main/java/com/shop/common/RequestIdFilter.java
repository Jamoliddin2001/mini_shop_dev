package com.shop.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a correlation id to every request so all log lines of one request can be stitched together
 * under load, and the client can quote it when reporting a problem.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} — before the Spring Security filter chain — so even
 * authentication/authorization logs carry the id. The id is taken from an inbound
 * {@code X-Request-Id} header when present (so an upstream gateway/trace id is preserved) or generated.
 * It is placed in the SLF4J {@link MDC} (rendered via {@code %X{requestId}} in the log pattern),
 * echoed back in the response header, and <b>always removed in a finally block</b> to avoid leaking it
 * onto a pooled worker thread.</p>
 *
 * <p>Security: the inbound header is untrusted, so it is sanitized to a safe charset and length before
 * touching the MDC — this prevents <b>log injection</b> (CRLF / forged log lines).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;
    private static final Pattern DISALLOWED = Pattern.compile("[^A-Za-z0-9._-]");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Returns a safe correlation id: the sanitized inbound header when it yields a non-empty value,
     * otherwise a freshly generated UUID.
     */
    private String resolveRequestId(String inbound) {
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String sanitized = DISALLOWED.matcher(inbound).replaceAll("");
        if (sanitized.length() > MAX_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LENGTH);
        }
        return sanitized.isEmpty() ? UUID.randomUUID().toString() : sanitized;
    }
}
