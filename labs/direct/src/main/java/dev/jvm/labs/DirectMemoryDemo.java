package dev.jvm.labs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class DirectMemoryDemo {
    public static void main(String[] args) {
        List<ByteBuffer> retained = new ArrayList<>();
        try {
            while (true) {
                retained.add(ByteBuffer.allocateDirect(1024 * 1024));
            }
        } catch (OutOfMemoryError expected) {
            System.out.println("caught=" + expected.getClass().getName());
            System.out.println("message=" + expected.getMessage() + " directMiB=" + retained.size());
        }
    }
}
