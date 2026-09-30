package poc.gate.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Envelope encryption for face templates.
 * - Each flight has its own random data key (DEK). Templates are encrypted with AES-256-GCM under that DEK.
 * - The DEK is stored only in wrapped (encrypted) form, under the key-encryption key (KEK) from a local keystore,
 *   which stands in for a KMS/HSM. Deleting the wrapped DEK makes every template of the flight unreadable,
 *   including copies in backups: that is crypto-shredding.
 * Plain Java (no Spring) so it is unit-tested directly.
 */
public class TemplateCrypto {

    public record Encrypted(byte[] iv, byte[] ciphertext) {
    }

    private static final int IV_BYTES = 12;   // 96-bit IV: the size GCM is designed for
    private static final int TAG_BITS = 128;  // authentication tag appended to the ciphertext

    private final SecretKey kek;
    private final SecureRandom random = new SecureRandom();

    public TemplateCrypto(SecretKey kek) {
        this.kek = kek;
    }

    /** Additional authenticated data: not secret, but the ciphertext only decrypts with exactly this value. */
    public static String aad(String flightId, String profileId, String modelId) {
        return flightId + "|" + profileId + "|" + modelId;
    }

    public SecretKey newDek() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot generate DEK", e);
        }
    }

    public byte[] wrap(SecretKey dek) {
        try {
            Cipher cipher = Cipher.getInstance("AES/KW/NoPadding"); // RFC 3394 key wrap, has its own integrity check
            cipher.init(Cipher.WRAP_MODE, kek);
            return cipher.wrap(dek);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot wrap DEK", e);
        }
    }

    public SecretKey unwrap(byte[] wrappedDek) {
        if (wrappedDek == null) {
            throw new IllegalStateException("no DEK: the flight's key was shredded or never created");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/KW/NoPadding");
            cipher.init(Cipher.UNWRAP_MODE, kek);
            Key key = cipher.unwrap(wrappedDek, "AES", Cipher.SECRET_KEY);
            return (SecretKey) key;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot unwrap DEK (wrong KEK or corrupted)", e);
        }
    }

    public Encrypted encrypt(SecretKey dek, float[] template, String aad) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv); // never reuse an IV with the same key: GCM breaks badly if you do
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new Encrypted(iv, cipher.doFinal(toBytes(template)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot encrypt template", e);
        }
    }

    public float[] decrypt(SecretKey dek, Encrypted encrypted, String aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(TAG_BITS, encrypted.iv()));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return toFloats(cipher.doFinal(encrypted.ciphertext())); // throws if ciphertext, IV or AAD changed
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot decrypt template", e);
        }
    }

    private static byte[] toBytes(float[] template) {
        ByteBuffer buffer = ByteBuffer.allocate(template.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(template);
        return buffer.array();
    }

    private static float[] toFloats(byte[] bytes) {
        float[] template = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(template);
        return template;
    }
}
