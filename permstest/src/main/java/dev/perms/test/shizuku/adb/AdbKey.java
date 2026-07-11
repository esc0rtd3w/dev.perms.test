package dev.perms.test.shizuku.adb;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;


import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import org.conscrypt.Conscrypt;
import java.security.Provider;

/**
 * ADB key + TLS material (ported from Shizuku Manager; minimal changes).
 *
 * Stores the private key encrypted using an AndroidKeyStore AES-GCM key.
 */
public final class AdbKey {

    private static final Provider CONSCRYPT_PROVIDER = Conscrypt.newProvider();

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ENCRYPTION_KEY_ALIAS = "_perms_test_adbkey_encryption_key_";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_SIZE_IN_BYTES = 12;
    private static final int TAG_SIZE_IN_BYTES = 16;

    public static final int ANDROID_PUBKEY_MODULUS_SIZE = 2048 / 8;
    private static final int ANDROID_PUBKEY_MODULUS_SIZE_WORDS = ANDROID_PUBKEY_MODULUS_SIZE / 4;
    private static final int RSAPublicKey_Size = 524;

    private static final byte[] PADDING = new byte[]{
            0x00, 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00,
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
            0x04, 0x14
    };

    private final Key encryptionKey;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final X509Certificate certificate;

    private final String name;

    public AdbKey(Context context, AdbKeyStore store, String name) {
        this.name = name;
        this.encryptionKey = getOrCreateEncryptionKey();
        this.privateKey = getOrCreatePrivateKey(store);
        this.publicKey = derivePublicKey(privateKey);
        this.certificate = buildCertificate(privateKey, publicKey);
    }

    public byte[] getAdbPublicKey() {
        return adbEncoded(publicKey, name);
    }

    public byte[] sign(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        cipher.update(PADDING);
        return cipher.doFinal(data);
    }

    public SSLContext getSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS", CONSCRYPT_PROVIDER);
        sslContext.init(new KeyManager[]{keyManager()}, new TrustManager[]{trustAllManager()}, new SecureRandom());
        return sslContext;
    }

    private Key getOrCreateEncryptionKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            Key existing = ks.getKey(ENCRYPTION_KEY_ALIAS, null);
            if (existing != null) return existing;

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    ENCRYPTION_KEY_ALIAS,
                    KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            kg.init(spec);
            return kg.generateKey();
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to init encryption key", t);
        }
    }

    private byte[] encrypt(byte[] plaintext, byte[] aad) throws Exception {
        byte[] ciphertext = new byte[IV_SIZE_IN_BYTES + plaintext.length + TAG_SIZE_IN_BYTES];
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
        if (aad != null) cipher.updateAAD(aad);
        cipher.doFinal(plaintext, 0, plaintext.length, ciphertext, IV_SIZE_IN_BYTES);
        System.arraycopy(cipher.getIV(), 0, ciphertext, 0, IV_SIZE_IN_BYTES);
        return ciphertext;
    }

    private byte[] decrypt(byte[] ciphertext, byte[] aad) throws Exception {
        GCMParameterSpec params = new GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, ciphertext, 0, IV_SIZE_IN_BYTES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, params);
        if (aad != null) cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext, IV_SIZE_IN_BYTES, ciphertext.length - IV_SIZE_IN_BYTES);
    }

    private RSAPrivateKey getOrCreatePrivateKey(AdbKeyStore store) {
        try {
            byte[] aad = new byte[16];
            byte[] tag = "adbkey".getBytes();
            System.arraycopy(tag, 0, aad, 0, Math.min(tag.length, aad.length));

            byte[] blob = store.get();
            if (blob != null && blob.length > 0) {
                try {
                    byte[] pkcs8 = decrypt(blob, aad);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
                } catch (Throwable ignored) {
                }
            }

            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA);
            kpg.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
            KeyPair kp = kpg.generateKeyPair();
            RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();

            byte[] enc = encrypt(priv.getEncoded(), aad);
            store.put(enc);
            return priv;
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to init RSA key", t);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), RSAKeyGenParameterSpec.F4));
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to derive public key", t);
        }
    }

    private static X509Certificate buildCertificate(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        try {
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
            var certHolder = new X509v3CertificateBuilder(
                    new X500Name("CN=00"),
                    BigInteger.ONE,
                    new Date(0),
                    new Date(2461449600L * 1000L),
                    Locale.ROOT,
                    new X500Name("CN=00"),
                    SubjectPublicKeyInfo.getInstance(publicKey.getEncoded())
            ).build(signer);
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certHolder.getEncoded()));
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to build cert", t);
        }
    }

    private X509ExtendedKeyManager keyManager() {
        return new X509ExtendedKeyManager() {
            private final String alias = "key";

            @Override
            public String chooseClientAlias(String[] keyTypes, java.security.Principal[] issuers, java.net.Socket socket) {
                if (keyTypes != null) {
                    for (String kt : keyTypes) {
                        if ("RSA".equals(kt)) return alias;
                    }
                }
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                if (this.alias.equals(alias)) return new X509Certificate[]{certificate};
                return null;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                if (this.alias.equals(alias)) return privateKey;
                return null;
            }

            @Override public String[] getClientAliases(String keyType, java.security.Principal[] issuers) { return null; }
            @Override public String[] getServerAliases(String keyType, java.security.Principal[] issuers) { return null; }
            @Override public String chooseServerAlias(String keyType, java.security.Principal[] issuers, java.net.Socket socket) { return null; }
        };
    }

    private static X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }

    private static int[] toAdbEncoded(BigInteger bi) {
        int[] encoded = new int[ANDROID_PUBKEY_MODULUS_SIZE_WORDS];
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger tmp = bi.add(BigInteger.ZERO);
        for (int i = 0; i < ANDROID_PUBKEY_MODULUS_SIZE_WORDS; i++) {
            BigInteger[] out = tmp.divideAndRemainder(r32);
            tmp = out[0];
            encoded[i] = out[1].intValue();
        }
        return encoded;
    }

    private static byte[] adbEncoded(RSAPublicKey key, String name) {
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n0inv = key.getModulus().remainder(r32).modInverse(r32).negate();
        BigInteger r = BigInteger.ZERO.setBit(ANDROID_PUBKEY_MODULUS_SIZE * 8);
        BigInteger rr = r.modPow(BigInteger.valueOf(2), key.getModulus());

        ByteBuffer buffer = ByteBuffer.allocate(RSAPublicKey_Size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(ANDROID_PUBKEY_MODULUS_SIZE_WORDS);
        buffer.putInt(n0inv.intValue());
        for (int v : toAdbEncoded(key.getModulus())) buffer.putInt(v);
        for (int v : toAdbEncoded(rr)) buffer.putInt(v);
        buffer.putInt(key.getPublicExponent().intValue());

        byte[] base64 = Base64.encode(buffer.array(), Base64.NO_WRAP);
        byte[] nameBytes = (" " + name + "\u0000").getBytes();
        byte[] out = new byte[base64.length + nameBytes.length];
        System.arraycopy(base64, 0, out, 0, base64.length);
        System.arraycopy(nameBytes, 0, out, base64.length, nameBytes.length);
        return out;
    }
}