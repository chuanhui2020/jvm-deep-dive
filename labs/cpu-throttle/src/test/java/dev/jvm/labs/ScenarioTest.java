package dev.jvm.labs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {
    @TempDir
    Path temporary;

    @Test
    void fixedScenarioCompletesInAnIsolatedJvm() throws Exception {
        Result result = run(new String[] {
            "fixed",
            "1",
            "1"
        });
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("mode=fixed"), result.output());
        assertTrue(result.output().contains("status=completed"), result.output());
        var progress = java.util.regex.Pattern.compile(
                "allocatedBlocks=(\\d+) allocatedPayloadMiB=([0-9.]+) elapsedMillis=(\\d+) payloadMiBPerSecond=([0-9.]+)")
                .matcher(result.output());
        assertTrue(progress.find(), result.output());
        assertTrue(Long.parseLong(progress.group(1)) > 0, result.output());
        assertTrue(Long.parseLong(progress.group(3)) > 0, result.output());
        assertTrue(Double.parseDouble(progress.group(4)) > 0, result.output());
    }

    @Test
    void rejectsAnUnknownMode() throws Exception {
        Result result = run(new String[] {"invalid"});
        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("IllegalArgumentException"), result.output());
    }

    private Result run(String[] arguments) throws Exception {
        var command = new ArrayList<String>();
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-Xms128m");
        command.add("-Xmx128m");
        command.add("-XX:+UseG1GC");
        command.add("-XX:ActiveProcessorCount=1");
        command.add("-cp");
        command.add(Path.of("target/classes").toAbsolutePath().toString());
        command.add("dev.jvm.labs.CpuQuotaDemo");
        Collections.addAll(command, arguments);
        Path log = Files.createTempFile(temporary, "scenario-", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Scenario timed out");
            return new Result(process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    private record Result(int exitCode, String output) {
    }
}
