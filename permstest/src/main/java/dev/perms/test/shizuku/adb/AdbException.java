package dev.perms.test.shizuku.adb;

public class AdbException extends Exception {
    public AdbException() {}
    public AdbException(String message) { super(message); }
    public AdbException(String message, Throwable cause) { super(message, cause); }
}
