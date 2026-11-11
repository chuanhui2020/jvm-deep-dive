package dev.jvm.labs;

public final class AllocationPressureDemo {
    private static volatile long sink;

    public static void main(String[] args) {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 60000;
        int sizeKiB = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        if (iterations < 1 || sizeKiB < 1 || sizeKiB > 4096) {
            throw new IllegalArgumentException("iterations > 0; sizeKiB between 1 and 4096");
        }
        byte[][] retained = new byte[Math.max(1, 4096 / sizeKiB)][];
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            byte[] block = new byte[sizeKiB * 1024];
            block[0] = (byte) i;
            retained[i % retained.length] = block;
            checksum += block[0];
        }
        sink = checksum + retained[0].length;
        System.out.println("allocatedMiB=" + ((long) iterations * sizeKiB / 1024));
        System.out.println("retainedSlots=" + retained.length + " checksum=" + sink);
    }
}
