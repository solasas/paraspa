package com.sashank.paraspa.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

/**
 * Import into a {@code @DataJpaTest} to count every SQL statement sent to the test database (see {@link StatementCounter}).
 * {@code COUNTER} is shared by the test JVM, so call {@code COUNTER.reset()} right before the code you measure.
 */
@TestConfiguration
public class CountingDataSourceConfig {

    public static final StatementCounter COUNTER = new StatementCounter();

    @Bean
    static BeanPostProcessor countingDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource ds && "dataSource".equals(beanName) ? COUNTER.wrap(ds) : bean;
            }
        };
    }
}
