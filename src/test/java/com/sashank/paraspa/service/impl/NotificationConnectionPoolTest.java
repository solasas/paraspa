package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.config.AsyncConfig;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.event.NotificationEventListener;
import com.sashank.paraspa.event.PostLikedEvent;
import com.sashank.paraspa.repository.NotificationRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.NotificationPushService;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Regression test for a bug found on PostgreSQL: an AFTER_COMMIT handler still holds the publishing request's database
 * connection, and creating the notification in its own transaction (REQUIRES_NEW) needs a second one. With more
 * simultaneous requests than the pool has connections, every request held one and waited for another: the pool was
 * starved, each request timed out after the connection timeout, and the notifications were lost.
 * <p>
 * This uses a deliberately tiny pool (3 connections) and 12 requests at once. Handling the events on their own small
 * executor (see AsyncConfig) lets each request release its connection first, so all 12 notifications are created.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // keep the real, pooled DataSource
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pooltest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Import({NotificationServiceImpl.class, NotificationEventListener.class, NotificationPushService.class,
        DtoMapper.class, AsyncConfig.class})
class NotificationConnectionPoolTest {

    private static final int CONCURRENT_REQUESTS = 12;   // far more than the 3 connections in the pool

    @MockBean private SimpMessagingTemplate messagingTemplate;

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void manyRequestsPublishingAtOnce_allGetTheirNotification_andNoneStarvesThePool() throws Exception {
        User author = userRepository.save(User.builder().name("Author").email("author@example.com").password("x").build());
        List<User> likers = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            likers.add(userRepository.save(User.builder().name("L" + i).email("l" + i + "@example.com").password("x").build()));
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService requests = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch everyoneReady = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Long>> finished = new ArrayList<>();
        for (User liker : likers) {
            finished.add(requests.submit(() -> {
                everyoneReady.countDown();
                go.await();
                long start = System.nanoTime();
                // one "request": a transaction that touches the database (holding a connection) and publishes an event
                tx.executeWithoutResult(status -> {
                    userRepository.findById(liker.getId());
                    publisher.publishEvent(new PostLikedEvent(100L, author.getId(), liker.getId()));
                });
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            }));
        }
        everyoneReady.await();
        go.countDown();

        // every request itself finishes quickly: it no longer waits for a second connection while holding the first
        for (Future<Long> request : finished) {
            assertThat(request.get(15, TimeUnit.SECONDS)).as("request duration in ms").isLessThan(1500);
        }
        requests.shutdown();

        // and every notification is created (asynchronously, shortly after commit)
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notificationRepository.countByRecipientIdAndReadFalse(author.getId())).isEqualTo(CONCURRENT_REQUESTS));
    }
}
