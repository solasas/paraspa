package com.sashank.paraspa.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashank.paraspa.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Rate limits login, register, refresh and message sending. Runs after {@link JwtAuthenticationFilter} so message
 * limits are per user; the others are per client IP (behind a reverse proxy set
 * {@code server.forward-headers-strategy=native} so that is the real client, not the proxy).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ruleFor(request) == null;
    }

    private RateLimiter.Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case "/api/auth/login" -> RateLimiter.Rule.LOGIN;
            case "/api/auth/register" -> RateLimiter.Rule.REGISTER;
            case "/api/auth/refresh" -> RateLimiter.Rule.REFRESH;
            case "/api/messages" -> RateLimiter.Rule.MESSAGE;
            default -> null;
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter.Rule rule = ruleFor(request);
        Object userId = request.getAttribute("userId");
        String key = (rule == RateLimiter.Rule.MESSAGE && userId != null) ? "user:" + userId : "ip:" + request.getRemoteAddr();

        RateLimiter.Decision decision = rateLimiter.tryAcquire(rule, key);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message("Too many requests. Try again in " + decision.retryAfterSeconds() + " seconds.")
                .path(request.getRequestURI())
                .build());
    }
}
