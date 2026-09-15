package com.ase.billing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every API call: who made it, what it was, how it came out and how long
 * it took. Nothing else in this application logs anything, which is exactly
 * why a bug like "the deleted number won't come back" or "the CNF rate change
 * didn't stick" is hard to chase after the fact — there is no trail. This
 * filter is the trail. It wraps every request, not just the ones that fail,
 * so a slow or silently-wrong call shows up here even when it returns 200.
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.ase.billing.http");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The SPA's own static assets are noise; the API and PDF endpoints are the point.
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String user = username();
            String line = "%s %s%s -> %d (%dms) [%s]".formatted(
                    method, uri, query == null ? "" : "?" + query, status, tookMs, user);
            if (status >= 500) {
                log.error(line);
            } else if (status >= 400) {
                log.warn(line);
            } else {
                log.info(line);
            }
        }
    }

    private String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}
