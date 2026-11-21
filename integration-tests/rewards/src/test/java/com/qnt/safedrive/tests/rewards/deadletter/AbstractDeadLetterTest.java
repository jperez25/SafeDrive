package com.qnt.safedrive.tests.rewards.deadletter;

import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.rewards.AbstractRewardsTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the dead-letter and replay feature of rewards (events it can never handle; provider failures are in
 * the notification-failure feature). Every test uses its own Kafka key, so replays of other tests do not matter: assert
 * "at least" on dead-letter counts.
 */
@Feature("Dead letter and replay")
public abstract class AbstractDeadLetterTest extends AbstractRewardsTest {

    protected TopicReader.RawMessage awaitDeadLetter(String key) {
        return tracer.awaitRawByKey(DEAD_LETTER_TOPIC, key, 1).getFirst();
    }
}
