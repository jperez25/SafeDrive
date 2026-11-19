package com.qnt.safedrive.tests.core.trace;

import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.util.List;

/** Reads a service's JSON log lines for a correlation ID ({@code kubectl logs}). */
public class LogReader {

    /** One structured log line of a service. */
    public record Line(String service, String json) {}

    private final TestEnvironment env;

    public LogReader(TestEnvironment env) {
        this.env = env;
    }

    public List<Line> readByCorrelationId(String service, String correlationId) {
        return Kubectl.run(30, "-n", env.namespace(), "logs", "deploy/" + service, "--since=30m")
                .stream()
                .filter(l -> l.contains(correlationId))
                .map(l -> new Line(service, l))
                .toList();
    }
}
