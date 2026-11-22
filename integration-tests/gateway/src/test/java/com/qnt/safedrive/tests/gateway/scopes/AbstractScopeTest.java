package com.qnt.safedrive.tests.gateway.scopes;

import com.qnt.safedrive.tests.gateway.AbstractGatewayTest;
import io.qameta.allure.Feature;

/**
 * Shared setup for the scope feature: a token only opens the routes of its scopes. {@code driver1} has every scope;
 * {@code readonly} has {@code score:read} only. Any answer other than 401 or 403 proves the call reached the service.
 */
@Feature("Scopes")
public abstract class AbstractScopeTest extends AbstractGatewayTest {}
