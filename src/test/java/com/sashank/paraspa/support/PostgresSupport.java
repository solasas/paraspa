package com.sashank.paraspa.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL for the tests that H2 cannot stand in for (native {@code ON CONFLICT}, row-value cursors, real
 * constraint and locking behaviour). One container is started lazily and shared by every test class of the run.
 *
 * <ul>
 *   <li>Normally a Testcontainers {@code postgres:16-alpine} (needs a Docker daemon).</li>
 *   <li>Or, without Docker, point at a scratch database you created yourself:
 *       {@code TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/paraspa_it TEST_DATABASE_USER=postgres TEST_DATABASE_PASSWORD=...}.
 *       The tests only add data (unique names) and never drop anything, but use an empty database, not a real one.</li>
 *   <li>With neither, the tests are skipped ({@link #available()}), so {@code ./mvnw test} still works anywhere.</li>
 * </ul>
 */
public final class PostgresSupport {

    private static PostgreSQLContainer<?> container;

    private PostgresSupport() {
    }

    private static boolean externalDatabase() {
        return System.getenv("TEST_DATABASE_URL") != null;
    }

    /** Used by {@code @EnabledIf("com.sashank.paraspa.support.PostgresSupport#available")}. */
    public static boolean available() {
        if (externalDatabase()) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    private static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();   // stopped by Testcontainers' Ryuk when the JVM exits
        }
        return container;
    }

    /** Call from a {@code @DynamicPropertySource} method. Flyway builds the schema; Hibernate only validates it. */
    public static void register(DynamicPropertyRegistry registry) {
        if (externalDatabase()) {
            registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
        } else {
            PostgreSQLContainer<?> pg = container();
            registry.add("spring.datasource.url", pg::getJdbcUrl);
            registry.add("spring.datasource.username", pg::getUsername);
            registry.add("spring.datasource.password", pg::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
}
