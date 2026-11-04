package com.sashank.paraspa.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Who may call what, through the real security filter chain (MockMvc): no token or a bad token is a 401 with the JSON
 * error body, a wrong role is a 403, and the public endpoints (auth, health, API docs) need no token.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security_mvc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.root=WARN",
        "logging.level.com.sashank.paraspa=WARN"
})
@AutoConfigureMockMvc
class SecurityMockMvcTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Value("${jwt.secret}") private String secret;

    private static final String CREDENTIALS = "{\"name\":\"Sec\",\"email\":\"%s\",\"password\":\"secret123\"}";

    private String registerAndGetToken(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS.formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("token").asText();
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    // ================================================================ no token -> 401

    @ParameterizedTest(name = "{0} {1} without a token is 401")
    @CsvSource({
            "GET,/api/profile/me", "GET,/api/feed", "GET,/api/explore", "GET,/api/posts", "GET,/api/notifications",
            "GET,/api/blocks", "GET,/api/matches", "GET,/api/messages/conversations", "GET,/api/follow-requests",
            "POST,/api/posts", "POST,/api/reports", "POST,/api/messages", "PUT,/api/users/1/block", "PUT,/api/users/1/follow",
            "DELETE,/api/posts/1", "PUT,/api/profile/me", "PUT,/api/profile/me/password", "GET,/api/admin/reports",
            "PUT,/api/admin/reports/1/resolve", "GET,/actuator/env", "GET,/actuator/metrics"
    })
    void noToken_is401_withAJsonErrorBody(String method, String path) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.message").value(containsString("Bearer")));
    }

    @Test
    void aGarbageToken_is401() throws Exception {
        mvc.perform(bearer(get("/api/profile/me"), "this.is.not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile/me").header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer ")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherKey_is401() throws Exception {
        String forged = Jwts.builder().setSubject("x@example.com").claim("userId", 1L)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-signing-key-that-is-long-enough-to-be-valid-for-hs512-0000".getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS512).compact();

        mvc.perform(bearer(get("/api/profile/me"), forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredToken_is401() throws Exception {
        String expired = Jwts.builder().setSubject("x@example.com").claim("userId", 1L)
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512).compact();

        mvc.perform(bearer(get("/api/profile/me"), expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenOfADeletedUser_is401() throws Exception {
        String token = registerAndGetToken("deleted-user@example.com");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users WHERE email = ?", "deleted-user@example.com");

        mvc.perform(bearer(get("/api/profile/me"), token)).andExpect(status().isUnauthorized());
    }

    // ================================================================ valid token -> allowed, wrong role -> 403

    @Test
    void aValidToken_getsThrough() throws Exception {
        String token = registerAndGetToken("valid-user@example.com");

        mvc.perform(bearer(get("/api/profile/me"), token)).andExpect(status().isOk());
        mvc.perform(bearer(get("/api/feed"), token)).andExpect(status().isOk());
    }

    @Test
    void aNormalUser_onTheAdminApi_is403_withAJsonErrorBody() throws Exception {
        String token = registerAndGetToken("plain-user@example.com");

        mvc.perform(bearer(get("/api/admin/reports"), token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
        mvc.perform(bearer(put("/api/admin/reports/1/resolve"), token)).andExpect(status().isForbidden());
    }

    @Test
    void anAdmin_onTheAdminApi_is200() throws Exception {
        String token = registerAndGetToken("an-admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "an-admin@example.com");

        mvc.perform(bearer(get("/api/admin/reports"), token)).andExpect(status().isOk());
    }

    @Test
    void actuatorEndpointsOtherThanHealth_stayClosedEvenForLoggedInUsers() throws Exception {
        String token = registerAndGetToken("actuator-user@example.com");

        mvc.perform(bearer(get("/actuator/env"), token)).andExpect(status().isNotFound());   // not exposed at all
        mvc.perform(bearer(get("/actuator/beans"), token)).andExpect(status().isNotFound());
    }

    // ================================================================ public endpoints

    @Test
    void authEndpoints_needNoToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());   // reached the controller (wrong credentials), not blocked by security
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"nope\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void health_isPublic_andShowsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void apiDocsAndSwaggerUi_arePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("paraspa API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/refresh']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout']").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void uploadedFiles_arePublic_soImageWidgetsNeedNoToken() throws Exception {
        mvc.perform(get("/files/missing.png")).andExpect(status().isNotFound());   // 404 (no such file), not 401
    }
}
