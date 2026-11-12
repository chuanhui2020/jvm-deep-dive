package dev.jvm.labs;

public final class AllocationChurnDemo {
    private static volatile byte[][] retained;
    private static volatile long sink;

    public static void main(String[] args) {
        String mode = LabSupport.mode(args);
        int seconds = LabSupport.number(args, 1, 10, 1, 300);
        int liveMiB = LabSupport.number(args, 2, 4, 1, 64);
        int blockKiB = LabSupport.number(args, 3, 4, 1, 64);
        LabSupport.begin(mode);
        int bytes = blockKiB * 1024;
        byte[][] ring = new byte[liveMiB * 1024 * 1024 / bytes][];
        retained = ring;
        long allocated = 0;
        if (mode.equals("fixed")) {
            for (int i = 0; i < ring.length; i++) {
                ring[i] = new byte[bytes];
                allocated += bytes;
            }
        }
        long started = System.nanoTime();
        long duration = seconds * 1_000_000_000L;
        long next = started + 1_000_000_000L;
        long sequence = 0;
        while (System.nanoTime() - started < duration) {
            int slot = (int) (sequence % ring.length);
            byte[] block = ring[slot];
            if (mode.equals("fault")) {
                block = new byte[bytes];
                ring[slot] = block;
                allocated += bytes;
            }
            block[0] = (byte) sequence;
            sink += block[0];
            sequence++;
            if (System.nanoTime() >= next) {
                System.out.println("allocatedPayloadMiB=" + allocated / 1024 / 1024);
                LabSupport.status("working");
                next += 1_000_000_000L;
            }
        }
        System.out.printf("allocatedPayloadMiB=%d boundedSlots=%d operations=%d checksum=%d%n",
                allocated / 1024 / 1024, ring.length, sequence, sink);
        LabSupport.complete();
    }
}
