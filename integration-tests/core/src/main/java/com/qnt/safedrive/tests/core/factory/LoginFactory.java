package com.qnt.safedrive.tests.core.factory;

import com.qnt.safedrive.tests.core.model.LoginRequest;
import com.qnt.safedrive.tests.core.model.constants.GatewayUsers;

/** Token requests for the seeded gateway users. */
public final class LoginFactory {

    private LoginFactory() {}

    /** A valid login of the seeded driver with every scope. */
    public static LoginRequest aLogin() {
        return new LoginRequest(GatewayUsers.DRIVER_EMAIL, GatewayUsers.PASSWORD);
    }
}
