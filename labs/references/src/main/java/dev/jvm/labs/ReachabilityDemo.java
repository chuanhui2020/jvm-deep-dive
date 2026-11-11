package dev.jvm.labs;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public final class ReachabilityDemo {
    static final class Node {
        Node next;
    }

    private static WeakReference<Node> createCycle(ReferenceQueue<Node> queue) {
        Node first = new Node();
        Node second = new Node();
        first.next = second;
        second.next = first;
        return new WeakReference<>(first, queue);
    }

    public static void main(String[] args) throws InterruptedException {
        ReferenceQueue<Node> queue = new ReferenceQueue<>();
        WeakReference<Node> weak = createCycle(queue);
        for (int i = 0; i < 20 && weak.get() != null; i++) {
            System.gc();
            Thread.sleep(25);
        }
        System.out.println("cleared=" + (weak.get() == null));
        System.out.println("enqueued=" + (queue.remove(1000) == weak));
        System.out.println("Explicit GC is a request, not a timing guarantee");
    }
}
