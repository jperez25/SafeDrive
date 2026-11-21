package com.qnt.safedrive.rewards;

import com.qnt.safedrive.commons.CorrelationId;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls the external notification provider (contract: docs/contracts/notification-provider.md). */
@Component
class NotificationClient {

    /** The provider is down, slow or throttling: worth retrying. */
    static class TransientFailureException extends RuntimeException {
        TransientFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final RestClient http;

    NotificationClient(@Value("${safedrive.rewards.notification-url}") String url,
                       @Value("${safedrive.rewards.notification-timeout-ms:2000}") long timeoutMs) {
        // HTTP/1.1 on purpose: the JDK client would try an HTTP/2 upgrade, which simple providers answer with a reset.
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /**
     * Sends the notification. The reward ID is the idempotency key, so a repeated call is harmless.
     * Throws {@link TransientFailureException} (retry) or {@link UnprocessableEventException} (permanent, dead-letter).
     */
    void send(UUID rewardId, String driverId, String kind) {
        try {
            http.post().uri("/v1/notifications")
                    .header("Idempotency-Key", rewardId.toString())
                    .header(CorrelationId.HTTP_HEADER, String.valueOf(CorrelationId.current()))
                    .body(Map.of("rewardId", rewardId.toString(), "driverId", driverId, "kind", kind))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 429) {
                throw new UnprocessableEventException("notification provider rejected the request with " + status, e);
            }
            throw new TransientFailureException("notification provider answered " + status, e);
        } catch (RuntimeException e) {
            throw new TransientFailureException("notification provider unreachable: " + e.getMessage(), e);
        }
    }
}
