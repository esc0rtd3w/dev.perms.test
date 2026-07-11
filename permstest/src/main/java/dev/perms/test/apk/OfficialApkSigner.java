package dev.perms.test.apk;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class OfficialApkSigner {
    private OfficialApkSigner() {
    }

    public static void sign(File unsignedApk, File signedApk, byte[] keyStoreBytes,
                     String keyAlias, String keyPassword) throws Exception {
        signInternal(unsignedApk, signedApk, keyStoreBytes, keyAlias, keyPassword, false);
    }

    public static void signPreservingAlignment(File unsignedApk, File signedApk, byte[] keyStoreBytes,
                                               String keyAlias, String keyPassword) throws Exception {
        signInternal(unsignedApk, signedApk, keyStoreBytes, keyAlias, keyPassword, true);
    }

    public static void signV1OnlyAndAlign(File unsignedApk, File signedApk, byte[] keyStoreBytes,
                                          String keyAlias, String keyPassword) throws Exception {
        File parent = signedApk == null ? null : signedApk.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create signed APK output directory.");
        }
        File tmp = File.createTempFile("perms-test-v1-signed-", ".apk", parent);
        try {
            signInternal(unsignedApk, tmp, keyStoreBytes, keyAlias, keyPassword, true, true, false, false);
            realignZipForV1Signature(tmp, signedApk);
            verifySignedApk(signedApk);
        } finally {
            try { tmp.delete(); } catch (Throwable ignored) {}
        }
    }

    private static void signInternal(File unsignedApk, File signedApk, byte[] keyStoreBytes,
                                     String keyAlias, String keyPassword, boolean preserveAlignment) throws Exception {
        signInternal(unsignedApk, signedApk, keyStoreBytes, keyAlias, keyPassword, preserveAlignment, true, true, true);
    }

    private static void signInternal(File unsignedApk, File signedApk, byte[] keyStoreBytes,
                                     String keyAlias, String keyPassword, boolean preserveAlignment,
                                     boolean v1, boolean v2, boolean v3) throws Exception {
        if (unsignedApk == null || !unsignedApk.isFile()) throw new IOException("Unsigned APK not found.");
        if (signedApk == null) throw new IOException("Signed APK output path is empty.");
        File parent = signedApk.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create signed APK output directory.");
        }

        KeyMaterial key = loadKey(keyStoreBytes, keyAlias, keyPassword);
        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                keyAlias,
                key.privateKey,
                Collections.singletonList(key.certificate))
                .build();

        ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .setV4SigningEnabled(false)
                .setDebuggableApkPermitted(true)
                .setOtherSignersSignaturesPreserved(false)
                .setAlignmentPreserved(preserveAlignment)
                .setCreatedBy("PermsTest")
                .build();
        signer.sign();
        verifySignedApk(signedApk);
    }


    private static void realignZipForV1Signature(File inputApk, File outputApk) throws IOException {
        try (ZipFile inZip = new ZipFile(inputApk);
             CountingOutputStream rawOut = new CountingOutputStream(new FileOutputStream(outputApk, false));
             ZipOutputStream out = new ZipOutputStream(rawOut)) {
            Enumeration<? extends ZipEntry> entries = inZip.entries();
            byte[] buffer = new byte[32768];
            while (entries.hasMoreElements()) {
                ZipEntry source = entries.nextElement();
                if (source == null || source.isDirectory()) continue;
                try (InputStream in = inZip.getInputStream(source)) {
                    if (source.getMethod() == ZipEntry.STORED || "resources.arsc".equals(source.getName())) {
                        byte[] data = readAll(in, source.getSize());
                        ZipEntry entry = new ZipEntry(source.getName());
                        entry.setTime(source.getTime() > 0 ? source.getTime() : System.currentTimeMillis());
                        entry.setMethod(ZipEntry.STORED);
                        entry.setSize(data.length);
                        entry.setCompressedSize(data.length);
                        entry.setCrc(crc32(data));
                        if ("resources.arsc".equals(source.getName())) {
                            entry.setExtra(buildAlignmentExtra(rawOut.getCount(), source.getName(), 4));
                        } else if (source.getExtra() != null) {
                            entry.setExtra(source.getExtra());
                        }
                        out.putNextEntry(entry);
                        out.write(data);
                        out.closeEntry();
                    } else {
                        ZipEntry entry = new ZipEntry(source.getName());
                        entry.setTime(source.getTime() > 0 ? source.getTime() : System.currentTimeMillis());
                        entry.setMethod(ZipEntry.DEFLATED);
                        out.putNextEntry(entry);
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        out.closeEntry();
                    }
                }
            }
        }
    }

    private static byte[] readAll(InputStream in, long expectedSize) throws IOException {
        int initial = expectedSize > 0L && expectedSize < Integer.MAX_VALUE ? (int) expectedSize : 32768;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(initial);
        byte[] buffer = new byte[32768];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static long crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return crc.getValue();
    }

    private static byte[] buildAlignmentExtra(long headerOffset, String name, int alignment) {
        if (alignment <= 1) return null;
        int nameBytes = name == null ? 0 : name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int fixedExtraBytes = 6;
        long baseDataOffset = headerOffset + 30L + nameBytes + fixedExtraBytes;
        int padding = (int) ((alignment - (baseDataOffset % alignment)) % alignment);
        byte[] extra = new byte[fixedExtraBytes + padding];
        extra[0] = 0x35;
        extra[1] = (byte) 0xd9;
        extra[2] = (byte) ((2 + padding) & 0xff);
        extra[3] = (byte) (((2 + padding) >>> 8) & 0xff);
        extra[4] = (byte) (alignment & 0xff);
        extra[5] = (byte) ((alignment >>> 8) & 0xff);
        return extra;
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        long getCount() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }

    private static KeyMaterial loadKey(byte[] keyStoreBytes, String alias, String password) throws Exception {
        if (keyStoreBytes == null || keyStoreBytes.length == 0) throw new IOException("Debug signing keystore is missing.");
        char[] pass = password == null ? new char[0] : password.toCharArray();
        try (InputStream in = new ByteArrayInputStream(keyStoreBytes)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, pass);
            Key key = ks.getKey(alias, pass);
            if (!(key instanceof PrivateKey)) throw new IOException("Debug signing key not found: " + alias);
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (cert == null) throw new IOException("Debug signing certificate not found: " + alias);
            return new KeyMaterial((PrivateKey) key, cert);
        }
    }

    private static void verifySignedApk(File signedApk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(signedApk).build().verify();
        if (result == null || !result.isVerified()) {
            String detail = result == null ? "no verifier result" : String.valueOf(result.getAllErrors());
            throw new IOException("Signed APK did not verify: " + detail);
        }
    }

    private static final class KeyMaterial {
        final PrivateKey privateKey;
        final X509Certificate certificate;

        KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
    }
}
