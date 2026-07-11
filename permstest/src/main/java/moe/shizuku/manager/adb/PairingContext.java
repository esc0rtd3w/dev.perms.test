package moe.shizuku.manager.adb;

import androidx.annotation.Keep;

@Keep
public final class PairingContext {
    private long nativePtr;

    private PairingContext(long nativePtr) {
        this.nativePtr = nativePtr;
    }

    static PairingContext create(byte[] password) {
        ShizukuAdbNative.ensureLoaded();
        long ptr = nativeConstructor(true, password);
        return ptr != 0L ? new PairingContext(ptr) : null;
    }

    byte[] msg() { return nativeMsg(nativePtr); }
    boolean initCipher(byte[] theirMsg) { return nativeInitCipher(nativePtr, theirMsg); }
    byte[] encrypt(byte[] inbuf) { return nativeEncrypt(nativePtr, inbuf); }
    byte[] decrypt(byte[] inbuf) { return nativeDecrypt(nativePtr, inbuf); }

    void destroy() {
        if (nativePtr != 0L) {
            nativeDestroy(nativePtr);
            nativePtr = 0L;
        }
    }

    private static native long nativeConstructor(boolean isClient, byte[] password);
    private static native byte[] nativeMsg(long nativePtr);
    private static native boolean nativeInitCipher(long nativePtr, byte[] theirMsg);
    private static native byte[] nativeEncrypt(long nativePtr, byte[] inbuf);
    private static native byte[] nativeDecrypt(long nativePtr, byte[] inbuf);
    private static native void nativeDestroy(long nativePtr);
}
