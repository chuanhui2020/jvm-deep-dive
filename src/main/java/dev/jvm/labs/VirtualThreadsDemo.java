package dev.jvm.labs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualThreadsDemo {
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        CountDownLatch entered = new CountDownLatch(12);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger virtualCount = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 12; i++) {
                Object independentLock = new Object();
                executor.submit(() -> {
                    synchronized (independentLock) {
                        if (Thread.currentThread().isVirtual()) {
                            virtualCount.incrementAndGet();
                        }
                        entered.countDown();
                        try {
                            entered.await();
                            Thread.sleep(200);
                            completed.incrementAndGet();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        }
        System.out.println("virtualTasks=" + virtualCount.get() + " completed=" + completed.get());
        System.out.println("elapsedMs=" + (System.nanoTime() - start) / 1000000);
        if (completed.get() != 12) {
            throw new AssertionError("Tasks did not complete");
        }
    }
}
