package org.github.tess1o.geopulse.admin.backup;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.Tag("unit")
class BackupEnvelopeTest {
    private byte[] encrypt(byte[] input) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(OutputStream encrypted=BackupEnvelope.encrypt(out,"a long recovery password".toCharArray())) { encrypted.write(input); }
        return out.toByteArray();
    }
    @Test void crossesStreamingSegmentsAndRandomizesEachArchive() throws Exception {
        byte[] data=new byte[3*1024*1024+17]; new Random(123).nextBytes(data);
        byte[] first=encrypt(data), second=encrypt(data);
        assertThat(first).isNotEqualTo(second);
        try(InputStream in=BackupEnvelope.decrypt(new ByteArrayInputStream(first),"a long recovery password".toCharArray())) { assertThat(in.readAllBytes()).isEqualTo(data); }
    }
    @Test void rejectsWrongPasswordTamperingTruncationAndLegacyZip() throws Exception {
        byte[] archive=encrypt(new byte[2*1024*1024]);
        assertThatThrownBy(()->BackupEnvelope.decrypt(new ByteArrayInputStream(archive),"wrong".toCharArray())).isInstanceOf(IOException.class);
        byte[] changed=archive.clone(); changed[changed.length-21]^=1;
        for(byte[] invalid:List.of(changed,Arrays.copyOf(archive,archive.length-1))) {
            assertThatThrownBy(()->{ try(InputStream in=BackupEnvelope.decrypt(new ByteArrayInputStream(invalid),"a long recovery password".toCharArray())) { in.transferTo(OutputStream.nullOutputStream()); } }).isInstanceOf(IOException.class);
        }
        assertThatThrownBy(()->BackupEnvelope.decrypt(new ByteArrayInputStream("PK-legacy-zip-file".getBytes()),"password".toCharArray())).hasMessageContaining("Unsupported backup format");
    }
    @Test void rejectsModifiedKdfParametersBeforeAllocatingMemory() throws Exception {
        byte[] archive=encrypt(new byte[0]); archive[16]^=1;
        assertThatThrownBy(()->BackupEnvelope.decrypt(new ByteArrayInputStream(archive),"password".toCharArray())).hasMessageContaining("parameters");
    }
    @Test void keyIdsDoNotIdentifyDifferentInstallationKeys() {
        KeyCipher a=new KeyCipher(new byte[32]); byte[] other=new byte[32]; other[0]=1; KeyCipher b=new KeyCipher(other);
        assertThat(a.fingerprint()).isNotEqualTo(b.fingerprint());
        String ciphertext=a.encrypt("secret");
        assertThat(a.decrypt(ciphertext,"v1")).isEqualTo("secret");
        assertThatThrownBy(()->b.decrypt(ciphertext,"v1")).isInstanceOf(IllegalStateException.class);
    }
}
