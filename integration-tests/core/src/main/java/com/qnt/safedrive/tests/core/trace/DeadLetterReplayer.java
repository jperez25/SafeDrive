package com.qnt.safedrive.tests.core.trace;

import io.qameta.allure.Step;
import com.qnt.safedrive.tests.core.utils.TestEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@code platform/scripts/replay-dlt.sh}, the same tool an operator uses. It copies every dead letter that is
 * new since the previous replay back to its source topic, so a replay also moves other tests' dead letters: assert
 * on your own key, never on totals.
 */
public class DeadLetterReplayer {

    private static final long TIMEOUT_SECONDS = 60;

    private final TestEnvironment env;

    public DeadLetterReplayer(TestEnvironment env) {
        this.env = env;
    }

    /** Replays {@code <topic>.<group>.dlt}; fails with the script output when it exits non-zero or takes too long. */
    @Step("Replay dead letters of {deadLetterTopic}")
    public void replay(String deadLetterTopic) {
        Path script = env.scriptsDir().resolve("replay-dlt.sh");
        Path output = null;
        try {
            // Output goes to a file, not a pipe: reading a pipe to its end would block forever on a stuck script and
            // the timeout below would never be reached.
            output = Files.createTempFile("replay-dlt-", ".log");
            Process process = new ProcessBuilder(script.toString(), deadLetterTopic)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); // kubectl exec children
                process.destroyForcibly();
                throw new IllegalStateException("replay of " + deadLetterTopic + " timed out after " + TIMEOUT_SECONDS
                        + "s: " + Files.readString(output));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("replay of " + deadLetterTopic + " failed: " + Files.readString(output));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot run " + script + " (run from the module directory)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }
}
