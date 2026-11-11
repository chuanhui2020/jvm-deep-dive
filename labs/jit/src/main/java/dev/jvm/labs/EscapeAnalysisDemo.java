package dev.jvm.labs;

import java.lang.management.ManagementFactory;

public final class EscapeAnalysisDemo {
    private record Point(int x, int y) {
    }

    private static volatile Object escaped;
    private static volatile long sink;

    private static long local(int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            Point point = new Point(i, i + 1);
            sum += point.x() + point.y();
        }
        return sum;
    }

    private static long escaping(int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            Point point = new Point(i, i + 1);
            escaped = point;
            sum += point.x() + point.y();
        }
        return sum;
    }

    public static void main(String[] args) {
        var base = ManagementFactory.getThreadMXBean();
        if (!(base instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException("Thread allocation accounting unavailable");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 20; i++) {
            sink = local(10000);
            sink = escaping(10000);
        }
        long id = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(id);
        sink = local(2000000);
        long localBytes = bean.getThreadAllocatedBytes(id) - before;
        before = bean.getThreadAllocatedBytes(id);
        sink = escaping(2000000);
        long escapingBytes = bean.getThreadAllocatedBytes(id) - before;
        System.out.println("localBytes=" + localBytes);
        System.out.println("escapingBytes=" + escapingBytes + " checksum=" + sink);
        System.out.println("Allocation observations depend on compilation and VM flags");
    }
}
