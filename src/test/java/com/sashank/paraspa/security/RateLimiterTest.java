package com.sashank.paraspa.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private RateLimitProperties properties;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setLogin(new RateLimitProperties.Rule(3, Duration.ofMinutes(1)));
        properties.setMessage(new RateLimitProperties.Rule(2, Duration.ofSeconds(10)));
        limiter = new RateLimiter(properties, nanos::get);
    }

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    @Test
    void allowsUpToTheCapacity_thenRefuses_withARetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()).isTrue();
        }

        RateLimiter.Decision refused = limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1");

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 20L);   // 3 per minute = one token every 20 s
    }

    @Test
    void tokensComeBackOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1");
        }
        assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()).isFalse();

        advance(Duration.ofSeconds(21));

        assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()).isTrue();
        assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()).isFalse();   // only one came back
    }

    @Test
    void aQuietClientNeverBanksMoreThanTheCapacity() {
        advance(Duration.ofHours(5));

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(3);
    }

    @Test
    void clientsAndRulesAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1");
        }

        assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()).isFalse();
        assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:2").allowed()).isTrue();      // another client
        assertThat(limiter.tryAcquire(RateLimiter.Rule.MESSAGE, "ip:1").allowed()).isTrue();    // another rule
    }

    @Test
    void whenDisabled_everythingPasses() {
        properties.setEnabled(false);

        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:1").allowed()).isTrue();
        }
    }

    @Test
    void idleBucketsAreForgotten_soMemoryDoesNotGrowWithEveryAddressEverSeen() {
        properties.setLogin(new RateLimitProperties.Rule(3, Duration.ofSeconds(1)));
        properties.setRegister(new RateLimitProperties.Rule(3, Duration.ofSeconds(1)));
        properties.setRefresh(new RateLimitProperties.Rule(3, Duration.ofSeconds(1)));
        properties.setMessage(new RateLimitProperties.Rule(3, Duration.ofSeconds(1)));
        for (int i = 0; i < 999; i++) {
            limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:" + i);
        }
        assertThat(limiter.size()).isEqualTo(999);

        advance(Duration.ofMinutes(5));
        limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:fresh");   // the 1000th call triggers the sweep

        assertThat(limiter.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void concurrentCallers_neverGetMoreThanTheCapacity() throws Exception {
        properties.setLogin(new RateLimitProperties.Rule(50, Duration.ofHours(1)));
        java.util.concurrent.atomic.AtomicInteger allowed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    if (limiter.tryAcquire(RateLimiter.Rule.LOGIN, "ip:shared").allowed()) {
                        allowed.incrementAndGet();
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(allowed.get()).isEqualTo(50);
    }
}
