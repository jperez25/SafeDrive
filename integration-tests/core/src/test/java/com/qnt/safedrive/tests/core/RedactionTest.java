package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.client.RedactingAllureFilter;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Waiter;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

public class RedactionTest {

    @Test
    public void masksSecretHeadersButKeepsCorrelationId() {
        Headers headers = new Headers(
                new Header("Authorization", "Bearer abc"),
                new Header("Idempotency-Key", "k-1"),
                new Header(Http.CORRELATION_HEADER, "corr-1"));
        String text = RedactingAllureFilter.redactHeaders(headers);
        assertThat(text).doesNotContain("Bearer abc").doesNotContain("k-1").contains("corr-1");
        assertThat(text).contains("Authorization: " + Http.REDACTED);
    }

    @Test
    public void masksSecretFieldsAtAnyDepth() {
        String text = RedactingAllureFilter.redactBody(
                "{\"tripId\":\"t1\",\"auth\":{\"Token\":\"s3cret\"},\"items\":[{\"password\":\"p\"}]}");
        assertThat(text).contains("t1").contains(Http.REDACTED).doesNotContain("s3cret").doesNotContain("\"p\"");
    }

    @Test
    public void leavesNonJsonBodyAlone() {
        assertThat(RedactingAllureFilter.redactBody("{not json")).isEqualTo("{not json");
    }

    @Test
    public void untilAttemptsStopsAfterTheGivenNumberOfTries() {
        Waiter waiter = new Waiter(Duration.ofSeconds(1), Duration.ofMillis(10));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> waiter.untilAttempts("never", 3, () -> {
                    calls.incrementAndGet();
                    return Optional.empty();
                }))
                .isInstanceOf(Waiter.WaitTimeoutException.class)
                .hasMessageContaining("3 attempts");
        assertThat(calls).hasValue(3);
    }

    @Test
    public void untilAttemptsReturnsAsSoonAsPresent() {
        Waiter waiter = new Waiter(Duration.ofSeconds(1), Duration.ofMillis(10));
        AtomicInteger calls = new AtomicInteger();
        String result = waiter.untilAttempts("second", 3, () -> calls.incrementAndGet() == 2 ? Optional.of("ok") : Optional.empty());
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }
}
