package com.sashank.paraspa.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Register, login, refresh, logout and password change through the real HTTP stack. The subclasses run the same
 * scenarios on H2 (always) and on PostgreSQL (Testcontainers, when Docker is available).
 */
abstract class AbstractAuthFlowTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Autowired protected TestRestTemplate rest;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void tolerant401Client() {
        // the JDK's default HTTP client throws instead of returning a 401 that carries WWW-Authenticate
        rest.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    // ------------------------------------------------------------------ helpers

    protected record Session(String email, String password, long userId, String access, String refresh) {
    }

    @SuppressWarnings("unchecked")
    protected ResponseEntity<Map<String, Object>> post(String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    protected ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    protected HttpStatus status(ResponseEntity<?> r) {
        return HttpStatus.valueOf(r.getStatusCode().value());
    }

    protected Session register() {
        String email = "flow" + UNIQUE.incrementAndGet() + System.nanoTime() + "@example.com";
        ResponseEntity<Map<String, Object>> r = post("/api/auth/register", Map.of("name", "Flow", "email", email, "password", "secret123"), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return session(email, "secret123", r.getBody());
    }

    protected Session login(Session s, String password) {
        ResponseEntity<Map<String, Object>> r = post("/api/auth/login", Map.of("email", s.email(), "password", password), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return session(s.email(), password, r.getBody());
    }

    protected Session session(String email, String password, Map<String, Object> body) {
        return new Session(email, password, ((Number) body.get("id")).longValue(), (String) body.get("token"), (String) body.get("refreshToken"));
    }

    protected HttpStatus profileStatus(String accessToken) {
        return status(exchange(HttpMethod.GET, "/api/profile/me", null, accessToken));
    }

    protected ResponseEntity<Map<String, Object>> refresh(String refreshToken) {
        return post("/api/auth/refresh", Map.of("refreshToken", refreshToken), null);
    }

    // ================================================================ tokens are issued

    @Test
    void register_andLogin_returnAnAccessTokenThatLastsFifteenMinutes_andARefreshToken() {
        Session s = register();
        ResponseEntity<Map<String, Object>> login = post("/api/auth/login", Map.of("email", s.email(), "password", "secret123"), null);

        for (Map<String, Object> body : List.of(
                post("/api/auth/login", Map.of("email", s.email(), "password", "secret123"), null).getBody(), login.getBody())) {
            assertThat(body.get("token")).isInstanceOf(String.class);
            assertThat((String) body.get("refreshToken")).hasSizeGreaterThanOrEqualTo(43);
            assertThat(((Number) body.get("expiresIn")).longValue()).isEqualTo(900L);
            assertThat(body.get("type")).isEqualTo("Bearer");
        }
        assertThat(profileStatus(s.access())).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("refreshToken")).isNotEqualTo(s.refresh());
    }

    @Test
    void theDatabaseStoresOnlyAHash_neverTheTokenTheClientHolds() {
        Session s = register();

        List<String> stored = jdbc.queryForList("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, s.userId());

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0)).hasSize(64).isNotEqualTo(s.refresh()).doesNotContain(s.refresh());
    }

    @Test
    void aRefreshTokenIsNotAnAccessToken() {
        Session s = register();

        assertThat(profileStatus(s.refresh())).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ================================================================ refresh = rotation

    @Test
    void refresh_givesANewPair_andTheOldRefreshTokenStopsWorking() {
        Session s = register();

        ResponseEntity<Map<String, Object>> first = refresh(s.refresh());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        Session renewed = session(s.email(), s.password(), first.getBody());
        assertThat(renewed.refresh()).isNotEqualTo(s.refresh());
        assertThat(profileStatus(renewed.access())).isEqualTo(HttpStatus.OK);
        assertThat(refresh(renewed.refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);   // the chain keeps going
        assertThat(refresh(s.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);   // the old one is spent
    }

    @Test
    void replayingAnAlreadyUsedRefreshToken_signsOutEveryDeviceOfThatUser() {
        Session laptop = register();
        Session phone = login(laptop, "secret123");
        Session renewed = session(laptop.email(), "secret123", refresh(laptop.refresh()).getBody());

        // somebody presents the old token again: treated as theft
        assertThat(refresh(laptop.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(refresh(renewed.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);   // the thief's (or owner's) new token
        assertThat(refresh(phone.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);     // and the other device
        // logging in with the password still works: only the sessions were cut
        assertThat(refresh(login(laptop, "secret123").refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anotherUsersSessionsAreNeverAffectedByReuseDetection() {
        Session victim = register();
        Session bystander = register();
        refresh(victim.refresh());
        refresh(victim.refresh());   // replay -> revokes the victim's sessions only

        assertThat(refresh(bystander.refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anExpiredRefreshToken_is401() {
        Session s = register();
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE user_id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(1)), s.userId());

        ResponseEntity<Map<String, Object>> r = refresh(s.refresh());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody().get("message")).isEqualTo("Invalid or expired refresh token");
    }

    @Test
    void garbageAndMissingRefreshTokens_areRejected() {
        assertThat(refresh("not-a-real-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/auth/refresh", Map.of(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", ""), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void twoSimultaneousRefreshesOfTheSameToken_exactlyOneSucceeds() throws Exception {
        Session s = register();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> refresh(s.refresh()).getStatusCode().value());
            }
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                codes.add(f.get());
            }
            assertThat(codes.stream().filter(c -> c == 200).count()).isEqualTo(1);
            assertThat(codes.stream().filter(c -> c == 401).count()).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    // ================================================================ logout

    @Test
    void logout_revokesTheRefreshToken_andIsIdempotent() {
        Session s = register();

        assertThat(status(exchange(HttpMethod.POST, "/api/auth/logout", Map.of("refreshToken", s.refresh()), null))).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refresh(s.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(exchange(HttpMethod.POST, "/api/auth/logout", Map.of("refreshToken", s.refresh()), null))).isEqualTo(HttpStatus.NO_CONTENT);   // again
        assertThat(status(exchange(HttpMethod.POST, "/api/auth/logout", Map.of("refreshToken", "never-existed"), null))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(exchange(HttpMethod.POST, "/api/auth/logout", Map.of(), null))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void logout_onlyEndsThatDevice() {
        Session laptop = register();
        Session phone = login(laptop, "secret123");

        exchange(HttpMethod.POST, "/api/auth/logout", Map.of("refreshToken", laptop.refresh()), null);

        assertThat(refresh(phone.refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ================================================================ password change

    @Test
    void changingThePassword_revokesEveryRefreshToken_andTheNewPasswordLogsIn() {
        Session laptop = register();
        Session phone = login(laptop, "secret123");

        ResponseEntity<String> change = exchange(HttpMethod.PUT, "/api/profile/me/password",
                Map.of("currentPassword", "secret123", "newPassword", "brandNew456"), laptop.access());
        assertThat(status(change)).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(refresh(laptop.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(phone.refresh()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/auth/login", Map.of("email", laptop.email(), "password", "secret123"), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Session fresh = login(laptop, "brandNew456");
        assertThat(refresh(fresh.refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aFailedPasswordChange_revokesNothing() {
        Session s = register();

        ResponseEntity<String> change = exchange(HttpMethod.PUT, "/api/profile/me/password",
                Map.of("currentPassword", "wrong-password", "newPassword", "brandNew456"), s.access());

        assertThat(status(change)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refresh(s.refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ================================================================ errors

    @Test
    void wrongCredentialsAreStill400_notA401ThatClientsMightMistakeForAnExpiredToken() {
        Session s = register();

        assertThat(post("/api/auth/login", Map.of("email", s.email(), "password", "nope"), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
