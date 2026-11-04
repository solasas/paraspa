package com.sashank.paraspa.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setLogin(new RateLimitProperties.Rule(2, Duration.ofHours(1)));
        properties.setMessage(new RateLimitProperties.Rule(2, Duration.ofHours(1)));
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "rateLimiter", new RateLimiter(properties));
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private MockHttpServletResponse call(String method, String uri, String ip, Long userId, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        if (userId != null) {
            request.setAttribute("userId", userId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void thirdLoginFromTheSameAddressIs429_withRetryAfterAndTheJsonErrorBody() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        call("POST", "/api/auth/login", "1.1.1.1", null, chain);
        call("POST", "/api/auth/login", "1.1.1.1", null, chain);

        MockHttpServletResponse third = call("POST", "/api/auth/login", "1.1.1.1", null, chain);

        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(third.getHeader("Retry-After"))).isPositive();
        assertThat(third.getContentType()).startsWith("application/json");
        assertThat(third.getContentAsString()).contains("\"status\":429").contains("Too Many Requests").contains("\"path\":\"/api/auth/login\"");
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anotherAddressHasItsOwnAllowance() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/auth/login", "1.1.1.1", null, chain);
        }

        assertThat(call("POST", "/api/auth/login", "2.2.2.2", null, chain).getStatus()).isEqualTo(200);
    }

    @Test
    void messagesAreLimitedPerUser_notPerAddress() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        call("POST", "/api/messages", "1.1.1.1", 5L, chain);
        call("POST", "/api/messages", "1.1.1.1", 5L, chain);

        assertThat(call("POST", "/api/messages", "1.1.1.1", 5L, chain).getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/messages", "1.1.1.1", 6L, chain).getStatus()).isEqualTo(200);   // same address, other user
    }

    @Test
    void otherEndpointsAndMethodsAreNeverLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 20; i++) {
            assertThat(call("GET", "/api/messages/conversations", "1.1.1.1", 5L, chain).getStatus()).isEqualTo(200);
            assertThat(call("GET", "/api/messages", "1.1.1.1", 5L, chain).getStatus()).isEqualTo(200);   // only POST sends
            assertThat(call("POST", "/api/posts", "1.1.1.1", 5L, chain).getStatus()).isEqualTo(200);
        }
        verify(chain, times(60)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aRefusedRequestNeverReachesTheController() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        call("POST", "/api/auth/login", "9.9.9.9", null, chain);
        call("POST", "/api/auth/login", "9.9.9.9", null, chain);
        org.mockito.Mockito.clearInvocations(chain);

        call("POST", "/api/auth/login", "9.9.9.9", null, chain);

        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
