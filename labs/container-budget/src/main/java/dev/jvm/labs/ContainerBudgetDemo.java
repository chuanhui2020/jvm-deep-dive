package dev.jvm.labs;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ContainerBudgetDemo {
    private static final List<ByteBuffer> RETAINED = new ArrayList<>();

    private static String cgroup(String name) {
        try {
            Path path = Path.of("/sys/fs/cgroup", name);
            return Files.exists(path) ? Files.readString(path).trim() : "unavailable";
        } catch (java.io.IOException error) {
            return "unavailable";
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String mode = LabSupport.mode(args);
        boolean fault = mode.equals("fault");
        int targetMiB = LabSupport.number(args, 1, mode.equals("fault") ? 640 : 64, 1, 1024);
        LabSupport.begin(mode);
        System.out.println("cgroupV2MemoryMax=" + cgroup("memory.max"));
        int retainedMiB = 0;
        try {
            while (retainedMiB < targetMiB) {
                int chunkMiB = Math.min(4, targetMiB - retainedMiB);
                ByteBuffer buffer = ByteBuffer.allocateDirect(chunkMiB * 1024 * 1024);
                for (int offset = 0; offset < buffer.capacity(); offset += 4096) {
                    buffer.put(offset, (byte) 1);
                }
                RETAINED.add(buffer);
                retainedMiB += chunkMiB;
                System.out.println("directPayloadMiB=" + retainedMiB
                        + " cgroupV2MemoryCurrent=" + cgroup("memory.current"));
                LabSupport.status("native-pressure");
                Thread.sleep(20);
            }
        } catch (OutOfMemoryError error) {
            if (!fault) throw error;
            LabSupport.oom(error);
            System.out.println("javaOomCaught=true thisIsNotEvidenceOfOOMKilled");
        }
        System.out.println("retainedDirectMiB=" + retainedMiB);
        LabSupport.complete();
    }
}
