package com.qnt.safedrive.tests.scoring.triprevisions;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.events;
import static org.assertj.core.api.Assertions.assertThat;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

/** Events may arrive in any order and more than once: an older or repeated revision must change nothing. */
public class TripRevisionNegativeTest extends AbstractTripRevisionTest {

    @Test(groups = Groups.REGRESSION, description = "An older revision that arrives after a newer one is ignored: no new score and the score stays")
    @Story("Out of order")
    public void olderRevisionAfterANewerOneIsIgnored() {
        TripCompleted first = aTripCompleted().withEventCounts(events(DrivingEventKinds.CRASH, 1));
        String correlationId = Ids.correlation();
        TripCompleted revised = first.withRevision(2).withEventCounts(events(DrivingEventKinds.HARD_BRAKE, 5));
        publishAndAwaitScore(revised, correlationId, 1);

        publishTripCompleted(first, correlationId);

        assertScoreUpdatedTimes(1, first.driverId(), correlationId);
        Validator.with(scoringClient().getScoreWithRetry(first.driverId(), correlationId))
                .hasStatus(Http.OK)
                .scoreBody()
                .hasScore(90.0)
                .hasScoreVersion(1);
    }

    @Test(groups = Groups.REGRESSION, description = "The same revision resent under a new event ID, even with different counts, is ignored")
    @Story("Duplicates")
    public void sameRevisionWithNewEventIdIsIgnored() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(trip, correlationId, 1);

        publishTripCompleted(trip.withEventCounts(events(DrivingEventKinds.CRASH, 3)), correlationId);

        assertScoreUpdatedTimes(1, trip.driverId(), correlationId);
        assertThat(scoreUpdatedFor(trip.driverId(), correlationId).getFirst().envelope().payload().path("score").asDouble())
                .as("score after the ignored resend")
                .isEqualTo(100.0);
    }

    @Test(groups = Groups.REGRESSION, description = "An ignored stale revision does not block the next real one: version continues without a gap")
    @Story("Out of order")
    public void versionHasNoGapAfterAnIgnoredRevision() {
        TripCompleted trip = aTripCompleted();
        String correlationId = Ids.correlation();
        publishAndAwaitScore(trip.withRevision(2), correlationId, 1);
        publishTripCompleted(trip, correlationId);

        var updated = publishAndAwaitScore(trip.withRevision(3).withEventCounts(events(DrivingEventKinds.CRASH, 1)), correlationId, 2);

        Validator.with(updated).envelope().scoreUpdatedPayload().hasScoreVersion(2).hasScore(75.0);
    }
}
