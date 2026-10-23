package io.freemytv;

/** Platform process policy: Android 11/12 DreamActivity uses a local Binder callback in the default app process. */
final class TvCompat {
    private TvCompat() {}
    static boolean separateDream(int sdk) { return sdk>=30 && sdk<=32; }
}
