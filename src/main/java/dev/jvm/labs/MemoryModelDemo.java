package dev.jvm.labs;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public final class MemoryModelDemo {
    private static int payload;
    private static int observed;
    private static volatile boolean ready;
    private static volatile int counter;

    public static void main(String[] args) throws Exception {
        Thread reader = Thread.ofPlatform().start(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            observed = payload;
        });
        payload = 42;
        ready = true;
        reader.join();
        if (observed != 42) {
            throw new AssertionError("Volatile publication violated");
        }
        System.out.println("published=" + observed);

        CyclicBarrier gate = new CyclicBarrier(2);
        Runnable splitIncrement = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    int observed = counter;
                    gate.await();
                    counter = observed + 1;
                    gate.await();
                }
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        };
        Thread first = Thread.ofPlatform().start(splitIncrement);
        Thread second = Thread.ofPlatform().start(splitIncrement);
        first.join();
        second.join();

        AtomicInteger atomic = new AtomicInteger();
        Runnable atomicIncrement = () -> {
            for (int i = 0; i < 100; i++) {
                atomic.incrementAndGet();
            }
        };
        first = Thread.ofPlatform().start(atomicIncrement);
        second = Thread.ofPlatform().start(atomicIncrement);
        first.join();
        second.join();
        System.out.println("volatileRmw=" + counter + " atomic=" + atomic.get());
        if (counter != 100 || atomic.get() != 200) {
            throw new AssertionError("Unexpected read-modify-write result");
        }
    }
}
