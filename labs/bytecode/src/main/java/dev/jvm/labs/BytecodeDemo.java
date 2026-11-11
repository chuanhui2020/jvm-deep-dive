package dev.jvm.labs;

public final class BytecodeDemo {
    private volatile int value;

    public static int compute(int a, int b) {
        int sum = a + b;
        return sum * 2;
    }

    public synchronized void increment() {
        value++;
    }

    public void block(Object lock) {
        synchronized (lock) {
            value++;
        }
    }

    public static void main(String[] args) {
        System.out.println("result=" + compute(3, 4));
    }
}
