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

/** The edge under load: new drivers arrive at a steady rate and each posts one valid batch. */
public class IngestThroughputSimulation extends Simulation {

    private static final int PROVISIONAL_P99_MILLISECONDS = 5000; // catches hangs only; pin from the first baseline

    {
        LoadProfile profile = LoadProfile.current();
        HttpProtocolBuilder protocol = http
                .baseUrl(TestEnvironment.current().serviceUrl(Services.TELEMETRY_INGEST))
                .contentTypeHeader("application/json")
                .acceptHeader("application/json");

        ScenarioBuilder drivers = scenario("Drivers post a batch")
                .feed(Feeders.drivers())
                .exec(http("POST batch")
                        .post(Paths.INGEST_BATCHES)
                        .header(Http.CORRELATION_HEADER, "#{correlationId}")
                        .body(StringBody("#{body}"))
                        .check(status().is(Http.ACCEPTED)));

        setUp(drivers.injectOpen(
                        rampUsersPerSec(1).to(profile.ratePerSecond()).during(profile.ramp()),
                        constantUsersPerSec(profile.ratePerSecond()).during(profile.hold())))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(99).lt(PROVISIONAL_P99_MILLISECONDS));
    }
}
