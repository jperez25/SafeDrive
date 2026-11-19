package com.qnt.safedrive.tests.scoring.scorecalculation;

import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.aTripCompleted;
import static com.qnt.safedrive.tests.core.factory.TripCompletedFactory.noEvents;

import com.qnt.safedrive.tests.core.model.TripCompleted;
import com.qnt.safedrive.tests.core.model.constants.DrivingEventKinds;
import com.qnt.safedrive.tests.core.model.constants.Groups;
import com.qnt.safedrive.tests.core.utils.Ids;
import com.qnt.safedrive.tests.core.validator.Validator;
import io.qameta.allure.Story;
import java.util.HashMap;
import java.util.Map;
import org.testng.annotations.Test;

/** Valid but unusual trip summaries: the score must still be right, and nothing may be dead-lettered. */
public class ScoreCalculationNegativeTest extends AbstractScoreCalculationTest {

    @Test(groups = Groups.REGRESSION, description = "An event kind scoring does not know has no weight and does not break the trip")
    @Story("Unknown event kind")
    public void unknownEventKindDoesNotCount() {
        Map<String, Integer> counts = new HashMap<>(noEvents());
        counts.put("teleport", 7);
        TripCompleted trip = aTripCompleted().withEventCounts(counts);

        Validator.with(publishAndAwaitScore(trip, Ids.correlation(), 1)).envelope().scoreUpdatedPayload().hasScore(100.0);
    }

    @Test(groups = Groups.REGRESSION, description = "A trip with an empty eventCounts object scores 100")
    @Story("Unknown event kind")
    public void emptyEventCountsScoreHundred() {
        TripCompleted trip = aTripCompleted().withEventCounts(Map.of());

        Validator.with(publishAndAwaitScore(trip, Ids.correlation(), 1)).envelope().scoreUpdatedPayload().hasScore(100.0);
    }

    @Test(groups = Groups.REGRESSION, description = "Event kinds that were never reported are the same as zero events")
    @Story("Unknown event kind")
    public void missingKindsCountAsZero() {
        TripCompleted trip = aTripCompleted().withEventCounts(Map.of(DrivingEventKinds.SPEEDING, 1));

        Validator.with(publishAndAwaitScore(trip, Ids.correlation(), 1)).envelope().scoreUpdatedPayload().hasScore(97.0);
    }
}
