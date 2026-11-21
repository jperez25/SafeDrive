package com.qnt.safedrive.tests.rewards;

import com.qnt.safedrive.tests.core.AbstractTest;
import com.qnt.safedrive.tests.core.factory.EnvelopeFactory;
import com.qnt.safedrive.tests.core.model.Envelope;
import com.qnt.safedrive.tests.core.model.PremiumAdjusted;
import com.qnt.safedrive.tests.core.model.RewardsResponse;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.utils.Json;
import io.qameta.allure.Epic;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Shared setup for every rewards feature. Premiums are put straight on {@code premium.adjusted} (keyed by driver, like
 * policy-rating does), so each test controls the band and version. The provider is the WireMock mock: its admin API
 * shows what it received and makes it fail, always scoped to this test's driver, so parallel tests never interfere.
 */
@Epic("rewards")
public abstract class AbstractRewardsTest extends AbstractTest {

    /** How long to watch for something that must not happen (everything here is asynchronous). */
    protected static final Duration NOTHING_MORE_WINDOW = Duration.ofSeconds(5);

    protected static final String DEAD_LETTER_TOPIC = Topics.deadLetter(Topics.PREMIUM_ADJUSTED, Services.REWARDS);

    /** Puts a new {@code premium.adjusted} event on the topic; returns the event that was sent. */
    protected Envelope publishPremiumAdjusted(PremiumAdjusted premium, String correlationId) {
        Envelope event = EnvelopeFactory.aPremiumAdjustedEvent(premium, correlationId);
        publisher().send(Topics.PREMIUM_ADJUSTED, premium.driverId(), event);
        return event;
    }

    protected RewardsResponse rewardsOf(String driverId) {
        return Json.read(rewardsClient().getRewardsWithRetry(driverId, null).asString(), RewardsResponse.class);
    }

    /** Waits until the driver has a reward for the score version in the given status. */
    protected RewardsResponse.Reward awaitReward(String driverId, int scoreVersion, String status) {
        return waiter.until("reward of driver " + driverId + " v" + scoreVersion + " in status " + status, () -> rewardsOf(driverId)
                .rewards().stream()
                .filter(r -> r.scoreVersion() == scoreVersion && status.equals(r.status()))
                .findFirst());
    }

    /** Waits until the provider received at least {@code count} notifications for the driver. */
    protected List<JsonNode> awaitNotifications(String driverId, int count) {
        return waiter.until(count + " notification(s) for driver " + driverId, () -> {
            List<JsonNode> found = notificationMock().notificationsFor(driverId);
            return found.size() >= count ? Optional.of(found) : Optional.empty();
        });
    }

    /**
     * Watches the window for a notification beyond {@code count} (fails if one shows up), then returns what the
     * provider received, for a validator to check: {@code Validator.with(awaitNoMoreNotificationsThan(1, id)).hasCount(1)}.
     */
    protected List<JsonNode> awaitNoMoreNotificationsThan(int count, String driverId) {
        waiter.staysEmpty("more than " + count + " notification(s) for driver " + driverId, NOTHING_MORE_WINDOW, () -> {
            int found = notificationMock().notificationsFor(driverId).size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        return notificationMock().notificationsFor(driverId);
    }

    /**
     * Watches the window for a reward beyond {@code count} (fails if one shows up), then returns the rewards API
     * response, for a validator to check: {@code Validator.with(awaitNoMoreRewardsThan(1, id)).rewardsBody().hasRewardCount(1)}.
     */
    protected Response awaitNoMoreRewardsThan(int count, String driverId) {
        waiter.staysEmpty("more than " + count + " reward(s) for driver " + driverId, NOTHING_MORE_WINDOW, () -> {
            int found = rewardsOf(driverId).rewards().size();
            return found > count ? Optional.of(found) : Optional.empty();
        });
        return rewardsClient().getRewardsWithRetry(driverId, null);
    }

    /** The JSON body the provider received in a journal entry. */
    protected static JsonNode bodyOf(JsonNode received) {
        return Json.tree(received.path("body").asString());
    }

    /** A header the provider received (name matched ignoring case), or null. */
    protected static String headerOf(JsonNode received, String name) {
        for (var field : received.path("headers").properties()) {
            if (field.getKey().equalsIgnoreCase(name)) {
                JsonNode value = field.getValue();
                return value.isArray() ? value.path(0).asString() : value.asString();
            }
        }
        return null;
    }
}
