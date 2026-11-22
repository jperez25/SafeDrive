package com.qnt.safedrive.tests.gateway.proxy;

import com.qnt.safedrive.tests.core.model.constants.Topics;
import com.qnt.safedrive.tests.core.trace.TopicReader;
import com.qnt.safedrive.tests.gateway.AbstractGatewayTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the proxy feature: the gateway forwards a valid call unchanged and brings the service's answer
 * back unchanged (status and body), and the correlation ID travels with it.
 */
@Feature("Proxy")
public abstract class AbstractProxyTest extends AbstractGatewayTest {

    /** The {@code trip.raw} event ingest published for this batch. */
    protected TopicReader.Message awaitTripRaw(String tripId, String correlationId) {
        return tracer.awaitEvent(Topics.TRIP_RAW, correlationId, m -> tripId.equals(m.envelope().payloadText("tripId")));
    }
}
