package com.sashank.paraspa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    /**
     * Where notification events are handled. Deliberately separate from the request threads:
     * <ul>
     *   <li>An AFTER_COMMIT handler on the request thread still holds that request's database connection, and storing
     *       the notification (its own transaction) needs another one. With as many simultaneous requests as the pool
     *       has connections, every request holds one and waits for a second: the pool is starved, every request
     *       times out and the notifications are lost (seen with 10 concurrent likes on the default pool of 10).
     *       Handling the event on this executor lets the request commit and release its connection first.</li>
     *   <li>The request no longer waits for the notification to be stored and pushed.</li>
     * </ul>
     * Few threads on purpose: each one needs at most a single connection at a time, so this can never take the pool
     * from the requests. The queue is bounded; if it ever fills up (a flood), new notifications are dropped and
     * logged instead of slowing the requests down. Notifications are best effort, the actions they describe are not.
     */
    @Bean(NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Notification queue is full ({} waiting); dropping a notification", pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
