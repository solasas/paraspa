package com.sashank.paraspa.e2e;

import com.sashank.paraspa.support.PostgresSupport;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The same auth scenarios on real PostgreSQL with the real Flyway migrations (skipped when no PostgreSQL is available). */
@EnabledIf("com.sashank.paraspa.support.PostgresSupport#available")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "logging.level.root=WARN",
        "logging.level.com.sashank.paraspa=WARN"
})
class AuthFlowPostgresTest extends AbstractAuthFlowTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }
}
