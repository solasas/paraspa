package com.sashank.paraspa.e2e;

import org.springframework.boot.test.context.SpringBootTest;

/** The auth scenarios on the in-memory database; always runs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_flow_e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class AuthFlowH2EndToEndTest extends AbstractAuthFlowTest {
}
