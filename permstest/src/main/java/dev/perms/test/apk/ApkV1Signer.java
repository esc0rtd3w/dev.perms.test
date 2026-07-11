package dev.perms.test.apk;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class ApkV1Signer {
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private static final String CERT_SF_NAME = "META-INF/CERT.SF";
    private static final String CERT_RSA_NAME = "META-INF/CERT.RSA";

    private ApkV1Signer() {
    }

    static void sign(File unsignedApk, File signedApk, InputStream keyStoreInput,
                     String keyAlias, String keyPassword) throws Exception {
        if (unsignedApk == null || !unsignedApk.isFile()) throw new IOException("Unsigned APK not found.");
        if (signedApk == null) throw new IOException("Signed APK output path is empty.");
        File parent = signedApk.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        KeyMaterial key = loadKey(keyStoreInput, keyAlias, keyPassword);
        List<EntryDigest> digests = collectEntryDigests(unsignedApk);
        byte[] manifest = buildManifest(digests);
        byte[] sf = buildSignatureFile(manifest, digests);
        byte[] rsa = buildSignatureBlock(sf, key.privateKey, key.certificate);
        writeSignedZip(unsignedApk, signedApk, manifest, sf, rsa);
    }

    private static KeyMaterial loadKey(InputStream in, String alias, String password) throws Exception {
        if (in == null) throw new IOException("Debug signing keystore is missing.");
        char[] pass = password == null ? new char[0] : password.toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(in, pass);
        Key key = ks.getKey(alias, pass);
        if (!(key instanceof PrivateKey)) throw new IOException("Debug signing key not found: " + alias);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        if (cert == null) throw new IOException("Debug signing certificate not found: " + alias);
        return new KeyMaterial((PrivateKey) key, cert);
    }

    private static List<EntryDigest> collectEntryDigests(File apk) throws Exception {
        List<EntryDigest> out = new ArrayList<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> e = zip.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String name = ze.getName();
                if (isSignatureEntry(name)) continue;
                md.reset();
                try (InputStream in = zip.getInputStream(ze)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
                }
                EntryDigest d = new EntryDigest();
                d.name = name;
                d.digest = b64(md.digest());
                d.manifestSection = buildDigestSection(name, d.digest);
                out.add(d);
            }
        }
        Collections.sort(out, (a, b) -> a.name.compareTo(b.name));
        return out;
    }

    private static byte[] buildManifest(List<EntryDigest> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeManifestLine(out, "Manifest-Version: 1.0");
        writeManifestLine(out, "Created-By: PermsTest");
        writeBlankLine(out);
        for (EntryDigest e : entries) {
            e.manifestSection = buildDigestSection(e.name, e.digest);
            out.write(e.manifestSection);
        }
        return out.toByteArray();
    }

    private static byte[] buildSignatureFile(byte[] manifest, List<EntryDigest> entries) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeManifestLine(out, "Signature-Version: 1.0");
        writeManifestLine(out, "Created-By: PermsTest");
        writeManifestLine(out, "SHA-256-Digest-Manifest: " + b64(md.digest(manifest)));
        writeBlankLine(out);
        for (EntryDigest e : entries) {
            writeManifestLine(out, "Name: " + e.name);
            md.reset();
            writeManifestLine(out, "SHA-256-Digest: " + b64(md.digest(e.manifestSection)));
            writeBlankLine(out);
        }
        return out.toByteArray();
    }

    private static byte[] buildDigestSection(String name, String digest) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeManifestLine(out, "Name: " + name);
        writeManifestLine(out, "SHA-256-Digest: " + digest);
        writeBlankLine(out);
        return out.toByteArray();
    }

    private static byte[] buildSignatureBlock(byte[] sf, PrivateKey key, X509Certificate cert) throws Exception {
        BouncyCastleProvider provider = new BouncyCastleProvider();
        List<X509Certificate> certs = Collections.singletonList(cert);
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider)
                .build(key);
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder()
                        .setProvider(provider)
                        .build())
                .build(signer, cert));
        gen.addCertificates(new JcaCertStore(certs));
        CMSSignedData signedData = gen.generate(new CMSProcessableByteArray(sf), false);
        return signedData.getEncoded();
    }

    private static String b64(byte[] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    private static void writeSignedZip(File unsignedApk, File signedApk, byte[] manifest, byte[] sf, byte[] rsa) throws IOException {
        try (ZipFile inZip = new ZipFile(unsignedApk);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(signedApk, false))) {
            writeBytesEntry(out, MANIFEST_NAME, manifest);
            writeBytesEntry(out, CERT_SF_NAME, sf);
            writeBytesEntry(out, CERT_RSA_NAME, rsa);

            Enumeration<? extends ZipEntry> entries = inZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze == null || ze.isDirectory()) continue;
                String name = ze.getName();
                if (isSignatureEntry(name)) continue;
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(ze.getTime());
                copy.setComment(ze.getComment());
                byte[] extra = ze.getExtra();
                if (extra != null) copy.setExtra(extra);
                if (ze.getMethod() == ZipEntry.STORED) {
                    copy.setMethod(ZipEntry.STORED);
                    copy.setSize(ze.getSize());
                    copy.setCompressedSize(ze.getSize());
                    copy.setCrc(ze.getCrc());
                }
                out.putNextEntry(copy);
                try (InputStream in = inZip.getInputStream(ze)) {
                    copyStream(in, out);
                }
                out.closeEntry();
            }
        }
    }

    private static void writeBytesEntry(ZipOutputStream out, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L);
        out.putNextEntry(e);
        out.write(data);
        out.closeEntry();
    }

    private static boolean isSignatureEntry(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase(Locale.US);
        if (!upper.startsWith("META-INF/")) return false;
        String base = upper.substring("META-INF/".length());
        return "MANIFEST.MF".equals(base)
                || base.endsWith(".SF")
                || base.endsWith(".RSA")
                || base.endsWith(".DSA")
                || base.endsWith(".EC");
    }

    private static void copyStream(InputStream in, ZipOutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }

    private static void writeManifestLine(ByteArrayOutputStream out, String line) throws IOException {
        byte[] bytes = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int offset = 0;
        int max = 72;
        while (offset < bytes.length) {
            int room = offset == 0 ? max : max - 1;
            int count = Math.min(room, bytes.length - offset);
            if (offset != 0) out.write(' ');
            out.write(bytes, offset, count);
            out.write('\r');
            out.write('\n');
            offset += count;
        }
        if (bytes.length == 0) {
            out.write('\r');
            out.write('\n');
        }
    }

    private static void writeBlankLine(ByteArrayOutputStream out) {
        out.write('\r');
        out.write('\n');
    }

    private static final class EntryDigest {
        String name;
        String digest;
        byte[] manifestSection;
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
