package dev.jvm.labs;

import java.util.concurrent.atomic.AtomicReference;

public final class ZgcAllocationStallDemo {
    private static volatile byte[][][] roots;

    public static void main(String[] args) throws InterruptedException {
        String mode = LabSupport.mode(args);
        int seconds = LabSupport.number(args, 1, 10, 1, 300);
        int liveMiB = LabSupport.number(args, 2, mode.equals("fault") ? 16 : 4, 1, 128);
        int workers = LabSupport.number(args, 3, 2, 1, 8);
        int blockKiB = LabSupport.number(args, 4, 64, 4, 256);
        LabSupport.begin(mode);
        int bytes = blockKiB * 1024;
        int slots = Math.max(1, liveMiB * 1024 * 1024 / workers / bytes);
        roots = new byte[workers][slots][];
        var failure = new AtomicReference<Throwable>();
        long[] counts = new long[workers];
        Thread[] threads = new Thread[workers];
        long started = System.nanoTime();
        for (int w = 0; w < workers; w++) {
            final int worker = w;
            threads[w] = Thread.ofPlatform().name("allocator-" + w).unstarted(() -> {
                long allocated = 0;
                try {
                    while (System.nanoTime() - started < seconds * 1_000_000_000L
                            && failure.get() == null) {
                        byte[] block = new byte[bytes];
                        block[0] = (byte) allocated;
                        roots[worker][(int) (allocated % slots)] = block;
                        allocated++;
                        if (mode.equals("fixed")) Thread.sleep(2);
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    counts[worker] = allocated;
                }
            });
            threads[w].start();
        }
        for (Thread thread : threads) thread.join();
        Throwable error = failure.get();
        if (error instanceof OutOfMemoryError oom && mode.equals("fault")) {
            LabSupport.oom(oom);
        } else if (error != null) {
            throw new IllegalStateException("allocator failed", error);
        }
        long blocks = 0;
        for (long count : counts) blocks += count;
        System.out.printf("allocatedPayloadMiB=%d boundedPayloadKiB=%d workers=%d%n",
                blocks * bytes / 1024 / 1024, (long) workers * slots * bytes / 1024, workers);
        System.out.println("inspectGcLog=Allocation Stall is observed by the JVM, not asserted by this program");
        LabSupport.complete();
    }
}
