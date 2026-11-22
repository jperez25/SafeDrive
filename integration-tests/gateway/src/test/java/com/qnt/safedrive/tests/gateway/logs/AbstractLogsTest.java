package com.qnt.safedrive.tests.gateway.logs;

import com.qnt.safedrive.tests.gateway.AbstractGatewayTest;
import io.qameta.allure.Feature;

/** Shared setup for the gateway log feature: logs carry the correlation ID and never a password or a token. */
@Feature("Logs")
public abstract class AbstractLogsTest extends AbstractGatewayTest {}
