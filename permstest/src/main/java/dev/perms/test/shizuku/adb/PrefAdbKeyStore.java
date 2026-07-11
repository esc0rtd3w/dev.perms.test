package dev.perms.test.shizuku.adb;

import android.content.SharedPreferences;
import android.util.Base64;

/** Simple SharedPreferences-backed ADB private-key store (encrypted by AdbKey). */
public final class PrefAdbKeyStore implements AdbKeyStore {

    private final SharedPreferences sp;
    private final String key;

    public PrefAdbKeyStore(SharedPreferences sp, String key) {
        this.sp = sp;
        this.key = key;
    }

    @Override
    public byte[] get() {
        String v = sp.getString(key, null);
        if (v == null || v.isEmpty()) return null;
        try {
            return Base64.decode(v, Base64.DEFAULT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void put(byte[] value) {
        if (value == null || value.length == 0) {
            sp.edit().remove(key).apply();
            return;
        }
        sp.edit().putString(key, Base64.encodeToString(value, Base64.NO_WRAP)).apply();
    }
}
