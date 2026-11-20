package com.qnt.safedrive.tests.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
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

/**
 * At-least-once delivery under load: each driver posts a batch (202), repeats it (200 duplicate) and reuses the batch
 * ID with different content (409). Under any rate the answers must stay these three, never a 5xx.
 */
public class DuplicatesUnderLoadSimulation extends Simulation {

    private static final int PROVISIONAL_P99_MILLISECONDS = 5000;

    {
        LoadProfile profile = LoadProfile.current();
        HttpProtocolBuilder protocol = http
                .baseUrl(TestEnvironment.current().serviceUrl(Services.TELEMETRY_INGEST))
                .contentTypeHeader("application/json")
                .acceptHeader("application/json");

        ScenarioBuilder drivers = scenario("Drivers repeat and change a batch")
                .feed(Feeders.drivers())
                .exec(http("POST batch").post(Paths.INGEST_BATCHES)
                        .header(Http.CORRELATION_HEADER, "#{correlationId}")
                        .body(StringBody("#{body}"))
                        .check(status().is(Http.ACCEPTED)))
                .exec(http("POST same batch again").post(Paths.INGEST_BATCHES)
                        .header(Http.CORRELATION_HEADER, "#{correlationId}")
                        .body(StringBody("#{body}"))
                        .check(status().is(Http.OK)))
                .exec(http("POST same batch ID, changed content").post(Paths.INGEST_BATCHES)
                        .header(Http.CORRELATION_HEADER, "#{correlationId}")
                        .body(StringBody("#{changedBody}"))
                        .check(status().is(Http.CONFLICT)));

        setUp(drivers.injectOpen(
                        rampUsersPerSec(1).to(profile.ratePerSecond()).during(profile.ramp()),
                        constantUsersPerSec(profile.ratePerSecond()).during(profile.hold())))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(99).lt(PROVISIONAL_P99_MILLISECONDS));
    }
}
