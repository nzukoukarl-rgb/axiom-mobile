package com.axiom.mobile;

// Skeleton: real UI in Kotlin/Compose lands with Android Studio packaging.
// Native methods will bind to libaxiom_android.so (cargo-ndk).

public class MainActivity {
    static {
        try {
            System.loadLibrary("axiom_android");
        } catch (UnsatisfiedLinkError e) {
            // Host unit tests / missing .so
        }
    }

    // Native API (implemented in Rust bridge once JNI wired)
    public static native String nativeIdentity();
    public static native String nativeInitWorkspace(String path);
    public static native String nativeExecute(String actionJson);
}
