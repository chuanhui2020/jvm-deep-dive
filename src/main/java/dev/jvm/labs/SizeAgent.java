package dev.jvm.labs;

import java.lang.instrument.Instrumentation;

public final class SizeAgent {
    private static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation value) {
        instrumentation = value;
    }

    public static long sizeOf(Object value) {
        if (instrumentation == null) {
            throw new IllegalStateException("Run layout through the script, or supply -javaagent");
        }
        return instrumentation.getObjectSize(value);
    }
}
