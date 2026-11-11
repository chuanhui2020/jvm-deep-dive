package dev.jvm.labs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcCycleDemoTest {
    @TempDir
    Path temporary;

    @Test
    void runsBoundedReferenceChurnThroughTheExistingEntryPoint() throws Exception {
        Result result = launch("1", "2", "4", "1");
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("holders=512 hotHolders=64"), result.output());
        assertTrue(result.output().contains("cyclesDone allocatedMiB="), result.output());
        assertTrue(result.output().contains("retainedPayloadMiB=2 hotPayloadKiB=256"), result.output());
    }

    @Test
    void rejectsAnInvalidDurationBeforeAllocatingTheLiveSet() throws Exception {
        Result result = launch("0", "512", "16", "1");
        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("IllegalArgumentException"), result.output());
    }

    private Result launch(String... arguments) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-Xmx32m");
        command.add("-cp");
        command.add(Path.of("target/classes").toAbsolutePath().toString());
        command.add("dev.jvm.labs.AllocationPressureDemo");
        command.add("cycles");
        java.util.Collections.addAll(command, arguments);
        Path log = Files.createTempFile(temporary, "cycles-", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Cycle workload timed out");
            return new Result(process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    private record Result(int exitCode, String output) {
    }
}
