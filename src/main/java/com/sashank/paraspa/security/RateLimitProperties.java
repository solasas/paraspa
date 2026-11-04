package com.sashank.paraspa.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limits, all overridable in configuration, e.g. {@code RATE_LIMIT_LOGIN_CAPACITY=20}. A rule means "at most
 * {@code capacity} requests per {@code period}" (a token bucket: the allowance refills continuously, and a quiet client
 * can burst up to {@code capacity}).
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Per client IP. */
    private Rule login = new Rule(10, Duration.ofMinutes(1));
    /** Per client IP. */
    private Rule register = new Rule(10, Duration.ofHours(1));
    /** Per client IP. */
    private Rule refresh = new Rule(30, Duration.ofMinutes(1));
    /** Per logged-in user, for REST and for the WebSocket chat together. */
    private Rule message = new Rule(30, Duration.ofMinutes(1));

    @Data
    public static class Rule {
        private int capacity;
        private Duration period;

        public Rule() {
        }

        public Rule(int capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }
    }
}
