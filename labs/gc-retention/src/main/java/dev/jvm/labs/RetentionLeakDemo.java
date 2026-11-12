package dev.jvm.labs;

import java.util.ArrayList;
import java.util.List;

public final class RetentionLeakDemo {
    private static final List<byte[]> CACHE = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        String mode = LabSupport.mode(args);
        boolean fault = mode.equals("fault");
        int steps = LabSupport.number(args, 1, 2048, 1, 100000);
        int blockKiB = LabSupport.number(args, 2, 64, 1, 1024);
        int pauseMillis = LabSupport.number(args, 3, 5, 0, 1000);
        LabSupport.begin(mode);
        int cap = Math.max(1, 8 * 1024 / blockKiB);
        try {
            for (int i = 0; i < steps; i++) {
                if (mode.equals("fixed") && CACHE.size() >= cap) {
                    CACHE.removeFirst();
                }
                CACHE.add(new byte[blockKiB * 1024]);
                if ((i & 63) == 0) {
                    System.out.println("retainedBlocks=" + CACHE.size());
                    LabSupport.status("retaining");
                }
                if (pauseMillis > 0) Thread.sleep(pauseMillis);
            }
        } catch (OutOfMemoryError error) {
            if (!fault) throw error;
            LabSupport.oom(error);
            System.out.println("retainedBlocks=" + CACHE.size() + " diagnosticGraphRetained=true");
        }
        System.out.println("retainedPayloadKiB=" + (long) CACHE.size() * blockKiB);
        LabSupport.complete();
    }
}
