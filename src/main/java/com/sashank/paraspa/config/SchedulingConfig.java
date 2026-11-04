package com.sashank.paraspa.config;

import com.sashank.paraspa.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Housekeeping jobs. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    @Autowired
    private RefreshTokenService refreshTokenService;

    /** Expired refresh tokens are useless; delete them once a day (03:30 server time). */
    @Scheduled(cron = "${refresh-token.purge-cron:0 30 3 * * *}")
    public void purgeExpiredRefreshTokens() {
        try {
            int deleted = refreshTokenService.purgeExpired();
            log.info("Purged {} expired refresh tokens", deleted);
        } catch (Exception e) {
            log.error("Could not purge expired refresh tokens", e);
        }
    }
}
