package com.qnt.safedrive.tests.rewards.notificationfailures;

import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.rewards.AbstractRewardsTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the notification-failure feature: the provider (WireMock) is made to fail for this test's driver only,
 * and the stub is removed after the test. The service retries 3 times with backoff (0.5 s, 1 s, 2 s), so a provider that
 * never recovers is called 4 times before the event is dead-lettered.
 */
@Feature("Notification failures")
public abstract class AbstractNotificationFailureTest extends AbstractRewardsTest {

    /** First call plus 3 retries. */
    protected static final int ATTEMPTS_BEFORE_DEAD_LETTER = 4;

    /** Makes the provider answer {@code status} for this driver until the returned stub is removed (or the test ends). */
    protected String failProvider(String driverId, int status) {
        String stub = notificationMock().failFor(driverId, status);
        onCleanup(() -> notificationMock().removeStub(stub));
        return stub;
    }

    protected TopicReader.RawMessage awaitDeadLetter(String key) {
        return tracer.awaitRawByKey(Topics.deadLetter(Topics.PREMIUM_ADJUSTED, Services.REWARDS), key, 1).getFirst();
    }
}
