package dev.jvm.labs;

public final class HumongousFragmentationDemo {
    private static volatile byte[][] anchors;
    private static volatile Object request;

    public static void main(String[] args) {
        String mode = LabSupport.mode(args);
        boolean fault = mode.equals("fault");
        int slots = LabSupport.number(args, 1, 48, 2, 256);
        int requestMiB = LabSupport.number(args, 2, 20, 1, 256);
        LabSupport.begin(mode);
        try {
            anchors = new byte[slots][];
            for (int i = 0; i < slots; i++) anchors[i] = new byte[600 * 1024];
            for (int i = 0; i < slots; i += 2) anchors[i] = null;
            System.out.println("phase=prepare-holes explicitGcRequested=true");
            System.gc();
            LabSupport.status("before-request");
            System.out.println("phase=request-start requestedPayloadMiB=" + requestMiB);
            if (mode.equals("fault")) {
                request = new byte[requestMiB * 1024 * 1024];
            } else {
                byte[][] segments = new byte[requestMiB * 4][];
                request = segments;
                for (int i = 0; i < segments.length; i++) segments[i] = new byte[256 * 1024];
            }
            System.out.println("requestSucceeded=true");
        } catch (OutOfMemoryError error) {
            if (!fault) throw error;
            LabSupport.oom(error);
        }
        LabSupport.complete();
    }
}
