package dev.jvm.labs;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class DiagnosticsDemo {
    private static volatile long sink;
    private static final List<byte[]> RETAINED = new ArrayList<>();

    private static void startDeadlock() {
        Object left = new Object();
        Object right = new Object();
        CountDownLatch acquired = new CountDownLatch(2);
        Thread.ofPlatform().daemon().name("deadlock-left")
                .start(() -> lockInOrder(left, right, acquired));
        Thread.ofPlatform().daemon().name("deadlock-right")
                .start(() -> lockInOrder(right, left, acquired));
    }

    private static void lockInOrder(Object first, Object second, CountDownLatch acquired) {
        synchronized (first) {
            acquired.countDown();
            try {
                acquired.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (second) {
                sink++;
            }
        }
    }

    private static void burn(long deadline) {
        long value = 1;
        while (System.nanoTime() < deadline) {
            for (int i = 0; i < 10000; i++) {
                value = value * 1664525 + 1013904223;
            }
        }
        sink = value;
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "cpu";
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        if (seconds < 1 || seconds > 300 || !List.of("cpu", "deadlock", "retention").contains(mode)) {
            throw new IllegalArgumentException("mode: cpu|deadlock|retention; seconds: 1..300");
        }
        System.out.println("pid=" + ProcessHandle.current().pid() + " mode=" + mode);
        long deadline = System.nanoTime() + seconds * 1000000000L;
        switch (mode) {
            case "cpu" -> burn(deadline);
            case "deadlock" -> {
                startDeadlock();
                Thread.sleep(seconds * 1000L);
                long[] threads = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
                System.out.println("deadlocked=" + (threads == null ? 0 : threads.length));
            }
            case "retention" -> {
                while (System.nanoTime() < deadline) {
                    if (RETAINED.size() < 24) {
                        RETAINED.add(new byte[1024 * 1024]);
                    }
                    Thread.sleep(100);
                }
                System.out.println("retainedMiB=" + RETAINED.size());
            }
            default -> throw new AssertionError(mode);
        }
        System.out.println("done checksum=" + sink);
    }
}
