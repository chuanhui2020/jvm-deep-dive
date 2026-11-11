package dev.jvm.labs;

import java.lang.management.ManagementFactory;

public final class EnvironmentDemo {
    public static void main(String[] args) {
        if (Runtime.version().feature() != 25) {
            throw new IllegalStateException("JDK 25 required");
        }
        System.out.println("runtime=" + Runtime.version());
        System.out.println("vm=" + System.getProperty("java.vm.name"));
        System.out.println("vendor=" + System.getProperty("java.vendor"));
        System.out.println("pid=" + ProcessHandle.current().pid());
        System.out.println("processors=" + Runtime.getRuntime().availableProcessors());
        System.out.println("maxHeapMiB=" + Runtime.getRuntime().maxMemory() / 1024 / 1024);
        System.out.println("flags=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
    }
}
