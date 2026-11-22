package com.qnt.safedrive.tests.gateway.tokenissuing;

import com.qnt.safedrive.tests.gateway.AbstractGatewayTest;
import io.qameta.allure.Feature;

/** Shared setup for the token feature ({@code POST /v1/auth/token}: credentials and an {@code X-Driver-Id} header in, a JWT out). */
@Feature("Token issuing")
public abstract class AbstractTokenIssuingTest extends AbstractGatewayTest {}
