package dev.jvm.labs;

/** A bounded live set with reference churn for observing generational collectors. */
public final class GcCycleDemo {
    private static final int BATCH_SIZE = 128;
    private static final int HOT_HOLDERS = 64;
    private static volatile Holder[] retainedRoots;
    private static volatile long sink;

    private static final class Holder {
        byte[] retained;
        byte[] recent;

        Holder(int blockBytes) {
            retained = new byte[blockBytes];
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 4) {
            throw new IllegalArgumentException("cycles: seconds liveMiB blockKiB pauseMillis");
        }
        int seconds = argument(args, 0, 20, 1, 300);
        int liveMiB = argument(args, 1, 32, 1, 512);
        int blockKiB = argument(args, 2, 16, 1, 1024);
        int pauseMillis = argument(args, 3, 1, 0, 1000);
        int blockBytes = blockKiB * 1024;
        int count = (int) ((long) liveMiB * 1024 * 1024 / blockBytes);
        Holder[] holders = new Holder[count];
        for (int i = 0; i < count; i++) {
            holders[i] = new Holder(blockBytes);
        }
        retainedRoots = holders;
        int hotCount = Math.min(HOT_HOLDERS, count);
        long allocatedBytes = (long) count * blockBytes;
        long sequence = 0;
        long checksum = 0;
        long started = System.nanoTime();
        long deadline = started + seconds * 1_000_000_000L;
        long nextReport = started + 1_000_000_000L;
        System.out.printf("cycles seconds=%d liveMiB=%d blockKiB=%d pauseMillis=%d holders=%d hotHolders=%d%n",
                seconds, liveMiB, blockKiB, pauseMillis, count, hotCount);
        System.out.println("pid=" + ProcessHandle.current().pid());
        while (System.nanoTime() < deadline) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                byte[] block = new byte[blockBytes];
                block[0] = (byte) sequence;
                block[block.length - 1] = (byte) (sequence >>> 8);
                // Long-lived holders repeatedly acquire references to newly allocated arrays.
                holders[(int) (sequence % hotCount)].recent = block;
                checksum += block[0] + block[block.length - 1];
                allocatedBytes += blockBytes;
                // Slowly replace the base live set instead of growing it without a bound.
                if ((sequence & 255) == 0) {
                    int slot = (int) ((sequence >>> 8) % count);
                    holders[slot].retained = new byte[blockBytes];
                    allocatedBytes += blockBytes;
                }
                sequence++;
            }
            long now = System.nanoTime();
            if (now >= nextReport) {
                System.out.printf("progress elapsedMs=%d allocatedMiB=%d%n",
                        (now - started) / 1_000_000L, allocatedBytes / 1024 / 1024);
                nextReport = now + 1_000_000_000L;
            }
            if (pauseMillis > 0) {
                Thread.sleep(pauseMillis);
            }
        }
        sink = checksum;
        System.out.printf("cyclesDone allocatedMiB=%d retainedPayloadMiB=%d hotPayloadKiB=%d checksum=%d%n",
                allocatedBytes / 1024 / 1024, (long) count * blockBytes / 1024 / 1024,
                (long) hotCount * blockBytes / 1024, sink);
        // Keep the bounded graph available for heap inspection during PodLauncher's hold window.
        java.lang.ref.Reference.reachabilityFence(retainedRoots);
    }

    private static int argument(String[] args, int index, int fallback, int minimum, int maximum) {
        int value = args.length > index ? Integer.parseInt(args[index]) : fallback;
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("argument " + index + " must be in " + minimum + ".." + maximum);
        }
        return value;
    }
}
