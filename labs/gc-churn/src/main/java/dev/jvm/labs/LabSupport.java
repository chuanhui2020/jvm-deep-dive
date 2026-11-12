package dev.jvm.labs;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

final class LabSupport {
    private static byte[] diagnosticReserve;

    static String mode(String[] args) {
        String mode = args.length == 0 ? "fault" : args[0];
        if (!mode.equals("fault") && !mode.equals("fixed")) {
            throw new IllegalArgumentException("mode must be fault or fixed");
        }
        return mode;
    }

    static int number(String[] args, int index, int fallback, int min, int max) {
        int value = args.length > index ? Integer.parseInt(args[index]) : fallback;
        if (value < min || value > max) {
            throw new IllegalArgumentException("argument " + index + " must be " + min + ".." + max);
        }
        return value;
    }

    static void begin(String mode) {
        if (Runtime.version().feature() != 25) {
            throw new IllegalStateException("JDK 25 required");
        }
        diagnosticReserve = new byte[4 * 1024 * 1024];
        if (oomMessage("warmup").isEmpty()) throw new AssertionError();
        System.out.println("mode=" + mode + " pid=" + ProcessHandle.current().pid());
        System.out.println("runtime=" + Runtime.version());
        System.out.println("flags=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        status("prepared");
    }

    static void status(String phase) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long collections = 0;
        long millis = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            collections += Math.max(0, bean.getCollectionCount());
            millis += Math.max(0, bean.getCollectionTime());
        }
        System.out.printf("phase=%s heapUsedKiB=%d heapMaxKiB=%d gcCount=%d gcTimeMs=%d%n",
                phase, heap.getUsed() / 1024, heap.getMax() / 1024, collections, millis);
    }

    static void oom(OutOfMemoryError error) {
        diagnosticReserve = null;
        System.out.println("caught=java.lang.OutOfMemoryError");
        System.out.println(oomMessage(error.getMessage()));
    }

    private static String oomMessage(String message) {
        return "message=" + message;
    }

    static void complete() {
        status("after-work");
        System.out.println("status=completed");
    }
}
