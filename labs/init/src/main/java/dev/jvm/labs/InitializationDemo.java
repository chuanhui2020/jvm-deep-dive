package dev.jvm.labs;

public final class InitializationDemo {
    static class Target {
        static final int CONSTANT = 7;
        static int runtime = initialize();

        static int initialize() {
            System.out.println("Target initialized");
            return 9;
        }
    }

    static class Parent {
        static int value = initialize();

        static int initialize() {
            System.out.println("Parent initialized");
            return 11;
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("constant=" + Target.CONSTANT);
        Class.forName("dev.jvm.labs.InitializationDemo$Target", false,
                InitializationDemo.class.getClassLoader());
        System.out.println("after passive use");
        System.out.println("runtime=" + Target.runtime);
        System.out.println("inherited=" + Child.value);
    }
}
