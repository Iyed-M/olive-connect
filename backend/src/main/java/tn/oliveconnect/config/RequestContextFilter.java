package tn.oliveconnect.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds correlation and caller context to every application log emitted during a request. */
@Component("oliveConnectRequestContextFilter")
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String TRACE_ID = "traceId";
    static final String USER_ID = "userId";
    static final String ENDPOINT = "endpoint";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String traceId = traceId(request);
        MDC.put(TRACE_ID, traceId);
        MDC.put(USER_ID, currentUserId());
        MDC.put(ENDPOINT, request.getMethod() + " " + request.getRequestURI());
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    }

    private String traceId(HttpServletRequest request) {
        String requestedTraceId = request.getHeader(TRACE_ID_HEADER);
        if (requestedTraceId == null || !SAFE_TRACE_ID.matcher(requestedTraceId).matches()) {
            requestedTraceId = request.getHeader("X-Request-Id");
        }
        return requestedTraceId != null && SAFE_TRACE_ID.matcher(requestedTraceId).matches()
                ? requestedTraceId
                : UUID.randomUUID().toString();
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "anonymous" : name;
    }
}
