package dev.jvm.labs;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runsWithExpectedSemanticsInAnIsolatedJvm(boolean observationWindow) throws Exception {
        var command = new ArrayList<String>();
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-Xmx64m");
        command.add("-cp");
        command.add(Path.of("target/classes").toAbsolutePath().toString());
        command.add(observationWindow ? "dev.jvm.labs.PodLauncher" : "dev.jvm.labs.AllocationPressureDemo");
        command.add("10000");
        command.add("4");
        Path log = temporary.resolve("child.log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().put("LAB_START_DELAY_SECONDS", "0");
        builder.environment().put("LAB_HOLD_SECONDS", "0");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Experiment timed out");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            if (observationWindow) {
                assertTrue(output.contains("phase=before pid="), output);
                assertTrue(output.contains("phase=after holdSeconds=0"), output);
            }
            assertTrue(output.contains("allocatedMiB=39"), output);
        } finally {
            process.destroyForcibly();
        }
    }
}
