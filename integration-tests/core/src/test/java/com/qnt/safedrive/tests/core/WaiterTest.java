package com.qnt.safedrive.tests.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qnt.safedrive.tests.core.utils.Waiter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

public class WaiterTest {

    private final Waiter waiter = new Waiter(Duration.ofSeconds(2), Duration.ofMillis(10));

    @Test
    public void returnsAsSoonAsResultIsPresent() {
        AtomicInteger calls = new AtomicInteger();
        String result = waiter.until("third call", () -> calls.incrementAndGet() == 3 ? Optional.of("ok") : Optional.empty());
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    public void keepsPollingAfterATransientError() {
        AtomicInteger calls = new AtomicInteger();
        String result = waiter.until("recovers", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("kubectl hiccup");
            }
            return Optional.of("ok");
        });
        assertThat(result).isEqualTo("ok");
    }

    @Test
    public void timesOutWithDescription() {
        Waiter quick = new Waiter(Duration.ofMillis(100), Duration.ofMillis(10));
        assertThatThrownBy(() -> quick.until("never happens", Optional::empty))
                .isInstanceOf(Waiter.WaitTimeoutException.class)
                .hasMessageContaining("never happens");
    }

    @Test
    public void staysEmptyFailsWhenSomethingAppears() {
        assertThatThrownBy(() -> waiter.staysEmpty("duplicate event", Duration.ofMillis(200), () -> Optional.of("seen")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("duplicate event");
    }

    @Test
    public void staysEmptyPassesWhenNothingAppears() {
        waiter.staysEmpty("nothing", Duration.ofMillis(100), Optional::empty);
    }
}
