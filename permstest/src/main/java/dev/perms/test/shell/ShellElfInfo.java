package dev.perms.test.shell;

import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Small ELF metadata helper used by the Shell bundled-binary UI.
 */
public final class ShellElfInfo {
    private ShellElfInfo() {
    }

    public static boolean isElfFile(File file) {
        try {
            if (file == null || !file.exists() || !file.isFile()) return false;
            byte[] header = new byte[4];
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                if (in.read(header) != 4) return false;
            }
            return isElfMagic(header, 4);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isElfAsset(ShellBinaryAssets assets, String assetName) {
        try {
            if (assets == null || TextUtils.isEmpty(assetName)) return false;
            byte[] header = new byte[4];
            try (InputStream in = new BufferedInputStream(assets.openBundledAsset(assetName))) {
                if (in.read(header) != 4) return false;
            }
            return isElfMagic(header, 4);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String describeAssetAbi(ShellBinaryAssets assets, String assetName) {
        try {
            if (assets == null || TextUtils.isEmpty(assetName)) return "";
            byte[] header = new byte[64];
            int count;
            try (InputStream in = new BufferedInputStream(assets.openBundledAsset(assetName))) {
                count = in.read(header);
            }
            return describeHeader(header, count);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String describeFileAbi(File file) {
        try {
            if (file == null || !file.exists() || !file.isFile()) return "";
            byte[] header = new byte[64];
            int count;
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                count = in.read(header);
            }
            return describeHeader(header, count);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String describeHeader(byte[] header, int count) {
        try {
            if (!isElfMagic(header, count) || count < 20) return "";
            int elfClass = header[4] & 0xFF;
            int elfData = header[5] & 0xFF;

            String className;
            if (elfClass == 1) className = "ELF32";
            else if (elfClass == 2) className = "ELF64";
            else className = "ELF?";

            boolean littleEndian = (elfData != 2);
            int offset = 18;
            if (count < offset + 2) return className;
            int machine = littleEndian
                    ? ((header[offset] & 0xFF) | ((header[offset + 1] & 0xFF) << 8))
                    : (((header[offset] & 0xFF) << 8) | (header[offset + 1] & 0xFF));

            return className + " " + describeMachine(machine, elfClass);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isElfMagic(byte[] header, int count) {
        return header != null
                && count >= 4
                && (header[0] & 0xFF) == 0x7F
                && header[1] == 'E'
                && header[2] == 'L'
                && header[3] == 'F';
    }

    private static String describeMachine(int machine, int elfClass) {
        switch (machine) {
            case 0x28:
                return (elfClass == 1) ? "ARM (32-bit)" : "ARM";
            case 0xB7:
                return "AArch64 (arm64)";
            case 0x03:
                return "x86";
            case 0x3E:
                return "x86_64";
            case 0x08:
                return "MIPS";
            case 0xF3:
                return "RISC-V";
            default:
                return "e_machine=0x" + Integer.toHexString(machine);
        }
    }
}
