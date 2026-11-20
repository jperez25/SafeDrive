package com.qnt.safedrive.telemetryingest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qnt.safedrive.commons.CorrelationId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Plain MockMvc over the controller and the correlation filter; the service is mocked. */
class TelemetryControllerTest {

    private final IngestService service = mock(IngestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TelemetryController(new BatchValidator(), service, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    private static String body(String ts) {
        return """
                {"batchId":"b-1","tripId":"t-1","driverId":"d-1","deviceId":"dev-1","batchSeq":0,"last":false,
                 "samples":[{"ts":"%s","lat":52.5,"lon":13.4,"speedKmh":50,"accelMs2":0.5,"heading":90,"phoneInUse":false}]}
                """.formatted(ts);
    }

    private static String recent() {
        return java.time.Instant.now().minusSeconds(30).toString();
    }

    @Test
    void acceptedReturns202() throws Exception {
        when(service.ingest(any(), any())).thenReturn(
                new IngestResult(IngestResult.Status.ACCEPTED, "b-1", "c-1", 1, List.of()));
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content(body(recent())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.acceptedSamples").value(1));
    }

    @Test
    void duplicateReturns200() throws Exception {
        when(service.ingest(any(), any())).thenReturn(
                new IngestResult(IngestResult.Status.DUPLICATE, "b-1", "c-1", 0, List.of()));
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content(body(recent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));
    }

    @Test
    void conflictReturns409() throws Exception {
        when(service.ingest(any(), any())).thenReturn(
                new IngestResult(IngestResult.Status.CONFLICT, "b-1", "c-1", 0, List.of()));
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content(body(recent())))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidBatchReturns400AndNeverTouchesTheService() throws Exception {
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("invalid"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correlationIdIsCreatedAtTheEdgeOrKept() throws Exception {
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(header().exists(CorrelationId.HTTP_HEADER));
        mvc.perform(post("/v1/telemetry/batches").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(CorrelationId.HTTP_HEADER, "caller-id"))
                .andExpect(header().string(CorrelationId.HTTP_HEADER, "caller-id"))
                .andExpect(jsonPath("$.correlationId").value("caller-id"));
    }
}
