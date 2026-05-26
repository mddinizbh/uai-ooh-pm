package com.uai.buslines.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that protects all {@code /api/internal/**} endpoints.
 *
 * <p>Requests to internal endpoints must supply the {@code X-UAI-Internal-Key} header
 * with a value that matches {@code buslines.internal.api-key} (set via the
 * {@code UAI_INTERNAL_API_KEY} environment variable).
 *
 * <p>A request is rejected with HTTP 401 when:
 * <ul>
 *   <li>The configured key is blank (no key configured — fail-safe default).</li>
 *   <li>The {@code X-UAI-Internal-Key} header is missing.</li>
 *   <li>The header value does not match the configured key.</li>
 * </ul>
 *
 * <p>Non-internal paths are passed through without inspection.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String INTERNAL_PATH_PREFIX = "/api/internal/";
    static final String API_KEY_HEADER = "X-UAI-Internal-Key";

    @Value("${buslines.internal.api-key:}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            String providedKey = request.getHeader(API_KEY_HEADER);
            if (internalApiKey.isBlank() || !internalApiKey.equals(providedKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
