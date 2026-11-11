package dev.jvm.labs;

import java.io.InputStream;

public final class ClassIdentityDemo {
    private static final String NAME = "dev.jvm.labs.ClassIdentityDemo$Payload";

    public static final class Payload {
        public Payload() {
        }
    }

    private static final class IsolatedLoader extends ClassLoader {
        private final byte[] bytes;

        IsolatedLoader(byte[] bytes) {
            super(ClassIdentityDemo.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                type = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes;
        try (InputStream in = ClassIdentityDemo.class.getResourceAsStream(
                "ClassIdentityDemo$Payload.class")) {
            if (in == null) {
                throw new IllegalStateException("Payload class file missing");
            }
            bytes = in.readAllBytes();
        }
        ClassLoader first = new IsolatedLoader(bytes);
        ClassLoader second = new IsolatedLoader(bytes);
        Class<?> a = first.loadClass(NAME);
        Class<?> b = second.loadClass(NAME);
        Object instance = b.getConstructor().newInstance();
        System.out.println("sameName=" + a.getName().equals(b.getName()));
        System.out.println("sameClass=" + (a == b));
        System.out.println("parentString=" + (first.loadClass("java.lang.String") == String.class));
        try {
            a.cast(instance);
            throw new AssertionError("Different defining loaders must produce distinct types");
        } catch (ClassCastException expected) {
            System.out.println("cast=ClassCastException");
        }
    }
}
