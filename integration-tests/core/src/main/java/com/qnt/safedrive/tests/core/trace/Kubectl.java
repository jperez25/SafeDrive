package com.qnt.safedrive.tests.core.trace;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs kubectl and returns stdout. How tests read service logs: black-box, no DB. */
final class Kubectl {

    private Kubectl() {}

    static List<String> run(long timeoutSeconds, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "kubectl";
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process process = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            // Read before waiting: a full pipe would block the process.
            List<String> lines = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))
                    .lines()
                    .toList();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("kubectl timed out: " + String.join(" ", args));
            }
            return lines;
        } catch (IOException e) {
            throw new IllegalStateException("cannot run kubectl (is it on the PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
