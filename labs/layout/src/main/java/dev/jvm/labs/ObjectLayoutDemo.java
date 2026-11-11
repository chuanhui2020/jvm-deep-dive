package dev.jvm.labs;

public final class ObjectLayoutDemo {
    static final class Empty {
    }

    static final class OneInt {
        int value;
    }

    static final class Reference {
        Object value = new byte[1024];
    }

    public static void main(String[] args) {
        System.out.println("Empty=" + SizeAgent.sizeOf(new Empty()));
        System.out.println("OneInt=" + SizeAgent.sizeOf(new OneInt()));
        System.out.println("Reference=" + SizeAgent.sizeOf(new Reference()));
        System.out.println("byte[0]=" + SizeAgent.sizeOf(new byte[0]));
        System.out.println("byte[1]=" + SizeAgent.sizeOf(new byte[1]));
        System.out.println("byte[1024]=" + SizeAgent.sizeOf(new byte[1024]));
    }
}
