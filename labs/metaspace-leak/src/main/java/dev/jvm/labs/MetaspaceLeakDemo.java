package dev.jvm.labs;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public final class MetaspaceLeakDemo {
    private static final List<ClassLoader> CACHE = new ArrayList<>();

    public static final class Payload {
        public long value;
        public long twice() { return value * 2; }
    }

    private static final class IsolatedLoader extends ClassLoader {
        IsolatedLoader() { super(MetaspaceLeakDemo.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }

    public static void main(String[] args) throws Exception {
        String mode = LabSupport.mode(args);
        boolean fault = mode.equals("fault");
        int iterations = LabSupport.number(args, 1, 12000, 1, 100000);
        int pauseMillis = LabSupport.number(args, 2, 0, 0, 1000);
        LabSupport.begin(mode);
        byte[] bytes;
        try (var in = MetaspaceLeakDemo.class.getResourceAsStream("MetaspaceLeakDemo$Payload.class")) {
            if (in == null) throw new IllegalStateException("Payload class resource missing");
            bytes = in.readAllBytes();
        }
        var bean = ManagementFactory.getClassLoadingMXBean();
        int defined = 0;
        try {
            for (int i = 0; i < iterations; i++) {
                if (mode.equals("fixed") && CACHE.size() >= 64) CACHE.clear();
                IsolatedLoader loader = new IsolatedLoader();
                loader.define(bytes);
                CACHE.add(loader);
                defined++;
                if ((i & 127) == 0) {
                    System.out.printf("defined=%d cachedLoaders=%d loaded=%d unloaded=%d%n",
                            defined, CACHE.size(), bean.getLoadedClassCount(), bean.getUnloadedClassCount());
                }
                if (pauseMillis > 0) Thread.sleep(pauseMillis);
            }
        } catch (OutOfMemoryError error) {
            if (!fault) throw error;
            int retained = CACHE.size();
            CACHE.clear();
            System.gc();
            LabSupport.oom(error);
            System.out.println("action=clear-loader-cache-for-diagnostics previouslyRetainedLoaders=" + retained);
        }
        System.out.printf("defined=%d cachedLoaders=%d unloaded=%d%n",
                defined, CACHE.size(), bean.getUnloadedClassCount());
        LabSupport.complete();
    }
}
