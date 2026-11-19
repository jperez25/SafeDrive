package com.qnt.safedrive.tripprocessor;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes trips whose last batch never arrived (or has a gap) after the inactivity timeout. */
@Component
class IdleTripCloser {

    private static final Logger log = LoggerFactory.getLogger(IdleTripCloser.class);

    private final TripService service;
    private final Duration timeout;

    IdleTripCloser(TripService service, @Value("${safedrive.trip.idle-timeout-s}") long timeoutSeconds) {
        this.service = service;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${safedrive.trip.idle-check-ms}")
    void run() {
        try {
            service.closeIdle(Instant.now().minus(timeout));
        } catch (Exception e) {
            log.warn("idle trip check failed, will retry: {}", e.toString());
        }
    }
}
