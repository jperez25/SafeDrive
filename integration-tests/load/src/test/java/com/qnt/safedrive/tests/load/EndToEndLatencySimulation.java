package com.qnt.safedrive.tests.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.asLongAs;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.group;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.qnt.safedrive.tests.core.model.constants.Http;
import com.qnt.safedrive.tests.core.model.constants.Paths;
import com.qnt.safedrive.tests.core.model.constants.Services;
import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;

/**
 * The whole pipeline under load: a driver posts a batch, then polls the premium API (the read edge) until the
 * premium exists. The wall-clock time of the group "ingest to premium" (the group <em>duration</em> in the Gatling
 * report) is the end-to-end latency a client sees: ingest, trip-processor, scoring and policy-rating, including the
 * consumer lag between them. Gatling cannot assert on a group duration (its group response time leaves out the
 * pauses), so a driver that waits longer than the limit sends one deliberately failing request, "SLO breach", which
 * shows in the report and counts in the failed-request assertion.
 */
public class EndToEndLatencySimulation extends Simulation {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final int MAX_POLLS = 120;                       // 60 s, the same wait the e2e tests allow
    // Per-driver limit from ingest to premium. Provisional (catches stuck pipelines only): pin it from the first baseline.
    private static final long PROVISIONAL_END_TO_END_SLO_MILLISECONDS = Long.getLong("load.e2e.slo.ms", 30_000L);
    private static final String GROUP = "ingest to premium";
    private static final int SLO_BREACH_MARKER = 999; // never a real status: the request exists only to fail visibly

    {
        LoadProfile profile = LoadProfile.current();
        TestEnvironment env = TestEnvironment.current();
        HttpProtocolBuilder ingest = http.baseUrl(env.serviceUrl(Services.TELEMETRY_INGEST))
                .contentTypeHeader("application/json").acceptHeader("application/json");

        ScenarioBuilder drivers = scenario("Drivers wait for their premium")
                .feed(Feeders.drivers())
                .exec(session -> session.set("polls", 0).set("premiumReady", false).set("startedAt", System.currentTimeMillis()))
                .group(GROUP).on(
                        exec(http("POST batch").post(Paths.INGEST_BATCHES)
                                .header(Http.CORRELATION_HEADER, "#{correlationId}")
                                .body(StringBody("#{body}"))
                                .check(status().is(Http.ACCEPTED)))
                        .exitHereIfFailed()
                        .exec(asLongAs(session -> !session.getBoolean("premiumReady") && session.getInt("polls") < MAX_POLLS).on(
                                pause(POLL_INTERVAL)
                                .exec(http("GET premium").get(env.serviceUrl(Services.POLICY_RATING) + Paths.driverPremium("#{driverId}"))
                                        .header(Http.CORRELATION_HEADER, "#{correlationId}")
                                        .check(status().in(Http.OK, Http.NOT_FOUND).saveAs("premiumStatus")))
                                .exec(session -> session
                                        .set("polls", session.getInt("polls") + 1)
                                        .set("premiumReady", session.getInt("premiumStatus") == Http.OK)))))
                .doIf(session -> !session.getBoolean("premiumReady")
                        || System.currentTimeMillis() - session.getLong("startedAt") > PROVISIONAL_END_TO_END_SLO_MILLISECONDS)
                .then(http("SLO breach: premium not ready in time").get(Paths.HEALTH_LIVENESS)
                        .check(status().is(SLO_BREACH_MARKER)));

        setUp(drivers.injectOpen(
                        rampUsersPerSec(1).to(profile.ratePerSecond()).during(profile.ramp()),
                        constantUsersPerSec(profile.ratePerSecond()).during(profile.hold())))
                .protocols(ingest)
                .assertions(global().failedRequests().percent().lt(1.0));
    }
}
