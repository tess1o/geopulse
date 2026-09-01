package org.github.tess1o.geopulse.admin.backup;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/** The existing database ciphertext format, usable without CDI or an application datasource. */
public final class KeyCipher {
    private final byte[] key;
    public KeyCipher(byte[] key) {
        if (key.length != 16 && key.length != 24 && key.length != 32) throw new IllegalArgumentException("Invalid AES key length");
        this.key = key.clone();
    }
    public static KeyCipher load(String location) {
        try {
            String encoded;
            if (location.startsWith("classpath:")) {
                try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(location.substring(10))) {
                    if (in == null) throw new IllegalArgumentException("Encryption key resource is missing");
                    encoded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                encoded = Files.readString(location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location));
            }
            return new KeyCipher(Base64.getDecoder().decode(encoded.trim()));
        } catch (Exception e) { throw new IllegalStateException("Cannot load the installation encryption key", e); }
    }
    public byte[] exportKey() { return key.clone(); }
    public String fingerprint() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, iv, plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Encryption failed", e); }
    }
    public String decrypt(String ciphertext, String keyId) {
        if (!"v1".equals(keyId)) throw new IllegalArgumentException("Unsupported encryption key ID");
        try {
            byte[] input = Base64.getDecoder().decode(ciphertext);
            if (input.length < 28) throw new GeneralSecurityException("Truncated ciphertext");
            return new String(crypt(Cipher.DECRYPT_MODE, Arrays.copyOf(input, 12), Arrays.copyOfRange(input, 12, input.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IllegalStateException("Secret decryption failed", e); }
    }
    private byte[] crypt(int mode, byte[] iv, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(input);
    }
}
