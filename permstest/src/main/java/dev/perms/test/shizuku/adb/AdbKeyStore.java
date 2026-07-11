package dev.perms.test.shizuku.adb;

public interface AdbKeyStore {
    byte[] get();
    void put(byte[] value);
}
