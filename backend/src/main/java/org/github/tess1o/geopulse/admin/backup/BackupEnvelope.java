package org.github.tess1o.geopulse.admin.backup;

import com.google.crypto.tink.*;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/** Versioned, password-wrapped Tink streaming keyset. No keys are written unencrypted. */
public final class BackupEnvelope {
    private static final byte[] MAGIC = "GEOPULSE-GPB\0".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1, MEMORY = 65536, ITERATIONS = 3, LANES = 1;
    static { try { StreamingAeadConfig.register(); } catch (GeneralSecurityException e) { throw new ExceptionInInitializerError(e); } }
    private BackupEnvelope() { }

    public static OutputStream encrypt(OutputStream destination, char[] password) throws IOException {
        requirePassword(password);
        try {
            byte[] salt = random(16), iv = random(12);
            byte[] prefix = ByteBuffer.allocate(MAGIC.length + 16 + 16 + 12)
                    .put(MAGIC).putInt(VERSION).putInt(MEMORY).putInt(ITERATIONS).putInt(LANES).put(salt).put(iv).array();
            KeysetHandle keys = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_1MB);
            ByteArrayOutputStream serialized = new ByteArrayOutputStream();
            CleartextKeysetHandle.write(keys, BinaryKeysetWriter.withOutputStream(serialized));
            byte[] clear = serialized.toByteArray();
            byte[] wrapped;
            try { wrapped = wrap(Cipher.ENCRYPT_MODE, password, salt, iv, prefix, clear); }
            finally { Arrays.fill(clear, (byte) 0); }
            byte[] header = ByteBuffer.allocate(prefix.length + 4 + wrapped.length).put(prefix).putInt(wrapped.length).put(wrapped).array();
            destination.write(header);
            return keys.getPrimitive(StreamingAead.class).newEncryptingStream(destination, header);
        } catch (GeneralSecurityException e) { throw new IOException("Cannot encrypt backup", e); }
    }

    public static InputStream decrypt(InputStream source, char[] password) throws IOException {
        requirePassword(password);
        try {
            DataInputStream in = new DataInputStream(source);
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Unsupported backup format. Only encrypted .gpb backups are supported.");
            int version = in.readInt(), memory = in.readInt(), iterations = in.readInt(), lanes = in.readInt();
            if (version != VERSION || memory != MEMORY || iterations != ITERATIONS || lanes != LANES)
                throw new IOException("Unsupported backup encryption parameters");
            byte[] salt = in.readNBytes(16), iv = in.readNBytes(12);
            if (salt.length != 16 || iv.length != 12) throw new EOFException("Truncated backup header");
            byte[] prefix = ByteBuffer.allocate(MAGIC.length + 44).put(MAGIC).putInt(version).putInt(memory).putInt(iterations).putInt(lanes).put(salt).put(iv).array();
            int length = in.readInt();
            if (length < 16 || length > 16384) throw new IOException("Invalid backup key envelope");
            byte[] wrapped = in.readNBytes(length);
            if (wrapped.length != length) throw new EOFException("Truncated backup key envelope");
            byte[] header = ByteBuffer.allocate(prefix.length + 4 + length).put(prefix).putInt(length).put(wrapped).array();
            byte[] clear = wrap(Cipher.DECRYPT_MODE, password, salt, iv, prefix, wrapped);
            try {
                KeysetHandle keys = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(clear));
                return keys.getPrimitive(StreamingAead.class).newDecryptingStream(in, header);
            } finally { Arrays.fill(clear, (byte) 0); }
        } catch (GeneralSecurityException e) { throw new IOException("Incorrect backup password or damaged backup", e); }
    }

    private static byte[] wrap(int mode, char[] password, byte[] salt, byte[] iv, byte[] aad, byte[] input) throws GeneralSecurityException {
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt).withMemoryAsKB(MEMORY).withIterations(ITERATIONS).withParallelism(LANES).build());
        byte[] key = new byte[32];
        try {
            generator.generateBytes(password, key);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } finally { Arrays.fill(key, (byte) 0); }
    }
    private static byte[] random(int size) { byte[] value = new byte[size]; new SecureRandom().nextBytes(value); return value; }
    private static void requirePassword(char[] password) {
        if (password == null || password.length == 0) throw new IllegalArgumentException("A backup password is required");
    }
}
