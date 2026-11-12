package dev.jvm.labs;

public final class G1EvacuationDemo {
    private static volatile byte[][] live;
    private static volatile byte[][] burst;

    public static void main(String[] args) throws InterruptedException {
        String mode = LabSupport.mode(args);
        boolean fault = mode.equals("fault");
        int rounds = LabSupport.number(args, 1, 40, 1, 10000);
        int liveMiB = LabSupport.number(args, 2, mode.equals("fault") ? 46 : 16, 1, 256);
        int burstMiB = LabSupport.number(args, 3, mode.equals("fault") ? 10 : 2, 1, 64);
        LabSupport.begin(mode);
        progress(-1, liveMiB, burstMiB);
        try {
            live = new byte[liveMiB * 8][];
            for (int i = 0; i < live.length; i++) live[i] = new byte[128 * 1024];
            for (int round = 0; round < rounds; round++) {
                burst = new byte[burstMiB * 8][];
                for (int i = 0; i < burst.length; i++) burst[i] = new byte[128 * 1024];
                progress(round, liveMiB, burstMiB);
                LabSupport.status("survival-pressure");
                Thread.sleep(10);
            }
        } catch (OutOfMemoryError error) {
            if (!fault) throw error;
            burst = null;
            if (live != null) {
                for (int i = live.length < 32 ? 0 : live.length - 32; i < live.length; i++) live[i] = null;
            }
            LabSupport.oom(error);
            System.out.println("action=drop-burst-and-up-to-4MiB-base-for-diagnostics");
        }
        LabSupport.complete();
    }
    private static void progress(int round, int liveMiB, int burstMiB) {
        System.out.println("round=" + round + " baseMiB=" + liveMiB + " burstMiB=" + burstMiB);
    }
}
