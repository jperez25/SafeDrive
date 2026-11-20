package com.qnt.safedrive.tests.core.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

public class AllureMetadataListenerTest {

    @Test
    public void environmentPropertiesNameTheEnvironmentAndItsServices() {
        assertThat(AllureMetadataListener.environmentProperties())
                .contains("environment=" + System.getProperty("env", "local"))
                .contains("service.telemetry-ingest=http://localhost:8081")
                .contains("namespace=safedrive");
    }
}
