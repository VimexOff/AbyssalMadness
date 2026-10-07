package abyssalmadness.client;

/** Client-side mirror of the Sleeping Stars event, plus when it last changed (for the fill-in/out animation). */
public final class StarClientState {
    private static volatile boolean awake = false;
    private static volatile long changedAtMs = 0L;

    private StarClientState() {
    }

    public static void set(boolean value) {
        if (value != awake) {
            awake = value;
            changedAtMs = System.currentTimeMillis();
        }
    }

    public static boolean isAwake() {
        return awake;
    }

    public static long changedAtMs() {
        return changedAtMs;
    }
}
