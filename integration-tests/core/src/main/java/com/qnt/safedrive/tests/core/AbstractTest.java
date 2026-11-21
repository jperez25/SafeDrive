package com.qnt.safedrive.tests.core;

import com.qnt.safedrive.tests.core.client.HealthClient;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.client.KafkaPublisher;
import com.qnt.safedrive.tests.core.client.NotificationMockClient;
import com.qnt.safedrive.tests.core.client.PremiumClient;
import com.qnt.safedrive.tests.core.client.RewardsClient;
import com.qnt.safedrive.tests.core.client.ScoringClient;
import com.qnt.safedrive.tests.core.client.TelemetryIngestClient;
import com.qnt.safedrive.tests.core.trace.CorrelationTracer;
import com.qnt.safedrive.tests.core.trace.DeadLetterReplayer;
import com.qnt.safedrive.tests.core.utils.Waiter;
import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.AfterMethod;

/**
 * Base of every test class. Gives access to the environment, clients, waiter and tracer, creates
 * unique IDs (tests are parallel-safe) and runs best-effort cleanup after each test method.
 */
public abstract class AbstractTest {

    protected final TestEnvironment env = TestEnvironment.current();
    protected final Waiter waiter = new Waiter(env.waitTimeout(), env.pollInterval());
    protected final CorrelationTracer tracer = new CorrelationTracer(env, waiter);

    // Per-thread so parallel methods in one class never share cleanup actions.
    private final ThreadLocal<List<Runnable>> cleanups = ThreadLocal.withInitial(ArrayList::new);

    protected TelemetryIngestClient ingestClient() {
        return new TelemetryIngestClient(env.serviceUrl(Services.TELEMETRY_INGEST));
    }

    protected ScoringClient scoringClient() {
        return new ScoringClient(env.serviceUrl(Services.SCORING));
    }

    protected PremiumClient premiumClient() {
        return new PremiumClient(env.serviceUrl(Services.POLICY_RATING));
    }

    protected RewardsClient rewardsClient() {
        return new RewardsClient(env.serviceUrl(Services.REWARDS));
    }

    protected NotificationMockClient notificationMock() {
        return new NotificationMockClient(env.serviceUrl(Services.NOTIFICATION_MOCK));
    }

    /** Publishes straight to a topic, for input the ingest API would refuse (poison events, repeated event IDs). */
    protected KafkaPublisher publisher() {
        return new KafkaPublisher(env);
    }

    protected DeadLetterReplayer replayer() {
        return new DeadLetterReplayer(env);
    }

    protected HealthClient healthClient(String service) {
        return new HealthClient(env.serviceUrl(service));
    }

    /** Registers an action to run after the current test method; failures are swallowed. */
    protected void onCleanup(Runnable action) {
        cleanups.get().add(action);
    }

    @AfterMethod(alwaysRun = true)
    public void runCleanups() {
        List<Runnable> actions = cleanups.get();
        for (int i = actions.size() - 1; i >= 0; i--) {
            try {
                actions.get(i).run();
            } catch (RuntimeException ignored) {
                // best effort: cleanup must never hide the test result
            }
        }
        actions.clear();
    }
}
