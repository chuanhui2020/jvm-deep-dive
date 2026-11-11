package dev.jvm.labs;

public final class StackDepthDemo {
    private static int depth;

    private static void recurse() {
        depth++;
        recurse();
    }

    public static void main(String[] args) {
        try {
            recurse();
        } catch (StackOverflowError expected) {
            System.out.println("caught=java.lang.StackOverflowError depth=" + depth);
        }
    }
}
