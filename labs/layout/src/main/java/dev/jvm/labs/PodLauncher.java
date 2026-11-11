package dev.jvm.labs;

import java.lang.reflect.InvocationTargetException;

/** Keeps observation windows inside the same JVM as the experiment. */
public final class PodLauncher {
    private static int seconds(String name, int fallback) {
        String value = System.getenv(name);
        int seconds = value == null ? fallback : Integer.parseInt(value);
        if (seconds < 0 || seconds > 3600) {
            throw new IllegalArgumentException(name + " must be between 0 and 3600");
        }
        return seconds;
    }

    public static void main(String[] args) throws Throwable {
        int delay = seconds("LAB_START_DELAY_SECONDS", 10);
        int hold = seconds("LAB_HOLD_SECONDS", 300);
        System.out.println("phase=before pid=" + ProcessHandle.current().pid()
                + " startDelaySeconds=" + delay);
        Thread.sleep(delay * 1000L);
        try {
            ObjectLayoutDemo.class.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
        System.out.println("phase=after holdSeconds=" + hold);
        Thread.sleep(hold * 1000L);
    }
}
