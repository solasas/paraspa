package com.sashank.paraspa.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The rate limits switched on, through the real filter chain: the Nth request is 429, other clients are untouched. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rate_limit_http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.root=WARN",
        "logging.level.com.sashank.paraspa=WARN",
        "rate-limit.enabled=true",
        "rate-limit.login.capacity=3",
        "rate-limit.login.period=1h",
        "rate-limit.register.capacity=2",
        "rate-limit.register.period=1h",
        "rate-limit.message.capacity=2",
        "rate-limit.message.period=1h"
})
@AutoConfigureMockMvc
class RateLimitingHttpTest {

    @Autowired private MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String loginBody(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";
    }

    @Test
    void login_isLimitedPerAddress_evenForWrongPasswords_whichIsTheBruteForceCase() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON).content(loginBody("victim@example.com")))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(post("/api/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON).content(loginBody("victim@example.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(containsString("Too many requests")));

        // somebody else on another address is unaffected
        mvc.perform(post("/api/auth/login").with(from("10.0.0.2")).contentType(MediaType.APPLICATION_JSON).content(loginBody("victim@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_isLimitedPerAddress() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/register").with(from("10.0.1.1")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"R\",\"email\":\"rl" + i + "@example.com\",\"password\":\"secret123\"}"))
                    .andExpect(status().isCreated());
        }

        mvc.perform(post("/api/auth/register").with(from("10.0.1.1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"R\",\"email\":\"rl-extra@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void messages_areLimitedPerUser_notPerAddress() throws Exception {
        String a = token("msg-a@example.com", "10.0.2.1");
        String b = token("msg-b@example.com", "10.0.2.1");   // the same address as A
        String body = "{\"receiverId\":999999,\"content\":\"hi\"}";   // the receiver does not exist: the request is still counted

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/messages").with(from("10.0.2.1")).header("Authorization", "Bearer " + a).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/messages").with(from("10.0.2.1")).header("Authorization", "Bearer " + a).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());

        mvc.perform(post("/api/messages").with(from("10.0.2.1")).header("Authorization", "Bearer " + b).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());   // B still has its own allowance
    }

    @Test
    void ordinaryReads_areNotLimited() throws Exception {
        String t = token("reader@example.com", "10.0.3.1");
        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/feed").with(from("10.0.3.1")).header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        }
    }

    private String token(String email, String ip) throws Exception {
        String body = mvc.perform(post("/api/auth/register").with(from(ip + "0")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();
    }
}
