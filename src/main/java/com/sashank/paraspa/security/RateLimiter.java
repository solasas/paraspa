package com.sashank.paraspa.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * In-memory token buckets, one per (rule, key). Fine for one instance; behind several instances each keeps its own
 * counts (use a shared store such as Redis then). Idle buckets are dropped so memory does not grow with every IP seen.
 */
@Component
public class RateLimiter {

    public enum Rule {LOGIN, REGISTER, REFRESH, MESSAGE}

    /** The answer for one request: {@code retryAfterSeconds} is 0 when allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
        long lastUsedNanos;
    }

    private static final int PURGE_EVERY = 1000;

    private final RateLimitProperties properties;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    @Autowired
    public RateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    RateLimiter(RateLimitProperties properties, LongSupplier nanoClock) {
        this.properties = properties;
        this.nanoClock = nanoClock;
    }

    public Decision tryAcquire(Rule rule, String key) {
        if (!properties.isEnabled()) {
            return Decision.ALLOWED;
        }
        RateLimitProperties.Rule limit = limitFor(rule);
        long now = nanoClock.getAsLong();
        double refillPerNano = (double) limit.getCapacity() / limit.getPeriod().toNanos();

        if (calls.incrementAndGet() % PURGE_EVERY == 0) {
            purgeIdle(now);
        }

        Bucket bucket = buckets.computeIfAbsent(rule + ":" + key, k -> {
            Bucket b = new Bucket();
            b.tokens = limit.getCapacity();
            b.lastRefillNanos = now;
            return b;
        });
        synchronized (bucket) {
            bucket.tokens = Math.min(limit.getCapacity(), bucket.tokens + (now - bucket.lastRefillNanos) * refillPerNano);
            bucket.lastRefillNanos = now;
            bucket.lastUsedNanos = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return Decision.ALLOWED;
            }
            long waitNanos = (long) Math.ceil((1 - bucket.tokens) / refillPerNano);
            return new Decision(false, Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L));
        }
    }

    private RateLimitProperties.Rule limitFor(Rule rule) {
        return switch (rule) {
            case LOGIN -> properties.getLogin();
            case REGISTER -> properties.getRegister();
            case REFRESH -> properties.getRefresh();
            case MESSAGE -> properties.getMessage();
        };
    }

    /** A bucket idle for longer than its full refill time is indistinguishable from a new one. */
    private void purgeIdle(long now) {
        long longestPeriod = Math.max(Math.max(properties.getLogin().getPeriod().toNanos(), properties.getRegister().getPeriod().toNanos()),
                Math.max(properties.getRefresh().getPeriod().toNanos(), properties.getMessage().getPeriod().toNanos()));
        buckets.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return now - e.getValue().lastUsedNanos > longestPeriod;
            }
        });
    }

    int size() {
        return buckets.size();
    }
}
