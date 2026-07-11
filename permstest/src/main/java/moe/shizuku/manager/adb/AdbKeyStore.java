package moe.shizuku.manager.adb;
public interface AdbKeyStore {
    byte[] get();
    void put(byte[] value);
}
