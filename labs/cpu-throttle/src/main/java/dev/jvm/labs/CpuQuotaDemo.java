package dev.jvm.labs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class CpuQuotaDemo {
    private static volatile byte[][][] roots;

    private static String cpuStat() {
        try {
            Path path = Path.of("/sys/fs/cgroup/cpu.stat");
            return Files.exists(path) ? Files.readString(path).replace('\n', ';') : "unavailable";
        } catch (java.io.IOException error) {
            return "unavailable";
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = LabSupport.mode(args);
        int seconds = LabSupport.number(args, 1, 30, 1, 300);
        int workers = LabSupport.number(args, 2, 2, 1, 8);
        LabSupport.begin(mode);
        System.out.println("cpuStatBefore=" + cpuStat());
        roots = new byte[workers][64][];
        var failure = new AtomicReference<Throwable>();
        long[] counts = new long[workers];
        Thread[] threads = new Thread[workers];
        long started = System.nanoTime();
        for (int w = 0; w < workers; w++) {
            final int worker = w;
            threads[w] = Thread.ofPlatform().name("load-" + w).unstarted(() -> {
                long n = 0;
                try {
                    while (System.nanoTime() - started < seconds * 1_000_000_000L
                            && failure.get() == null) {
                        byte[] block = new byte[32 * 1024];
                        block[0] = (byte) n;
                        roots[worker][(int) (n++ & 63)] = block;
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    counts[worker] = n;
                }
            });
            threads[w].start();
        }
        long[] lag = new long[seconds * 100 + 100];
        int samples = 0;
        while (System.nanoTime() - started < seconds * 1_000_000_000L && failure.get() == null) {
            long before = System.nanoTime();
            Thread.sleep(10);
            if (samples < lag.length) lag[samples++] = Math.max(0, System.nanoTime() - before - 10_000_000L);
        }
        for (Thread thread : threads) thread.join();
        long elapsedNanos = System.nanoTime() - started;
        if (failure.get() != null) throw new IllegalStateException("load worker failed", failure.get());
        long allocatedBlocks = 0;
        for (long count : counts) allocatedBlocks += count;
        double allocatedMiB = allocatedBlocks / 32.0;
        System.out.printf(java.util.Locale.ROOT,
                "allocatedBlocks=%d allocatedPayloadMiB=%.3f elapsedMillis=%d payloadMiBPerSecond=%.3f%n",
                allocatedBlocks, allocatedMiB, elapsedNanos / 1_000_000,
                allocatedMiB / (elapsedNanos / 1_000_000_000.0));
        Arrays.sort(lag, 0, samples);
        long p99 = samples == 0 ? 0 : lag[Math.min(samples - 1, (int) Math.ceil(samples * 0.99) - 1)];
        System.out.printf("heartbeatSamples=%d heartbeatLagP99Micros=%d%n", samples, p99 / 1000);
        System.out.println("cpuStatAfter=" + cpuStat());
        System.out.println("heartbeatIsNotBusinessP99=true modeDoesNotChangeWorkload=true");
        LabSupport.complete();
    }
}
