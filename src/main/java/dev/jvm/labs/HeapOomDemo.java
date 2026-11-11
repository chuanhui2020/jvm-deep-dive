package dev.jvm.labs;

import java.util.ArrayList;
import java.util.List;

public final class HeapOomDemo {
    public static void main(String[] args) {
        List<byte[]> retained = new ArrayList<>();
        try {
            while (true) {
                retained.add(new byte[1024 * 1024]);
            }
        } catch (OutOfMemoryError expected) {
            int count = retained.size();
            retained.clear();
            System.out.println("caught=" + expected.getClass().getName());
            System.out.println("message=" + expected.getMessage() + " retainedBlocks=" + count);
        }
    }
}
