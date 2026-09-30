package poc.gate.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import javax.crypto.AEADBadTagException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** Written before TemplateCrypto. No keystore needed: the test generates its own KEK. */
class TemplateCryptoTest {

    private final TemplateCrypto crypto = new TemplateCrypto(newAesKey());
    private final float[] template = randomTemplate();
    private final String aad = TemplateCrypto.aad("POC001", "P001", "r50-buffalo_l");

    private static SecretKey newAesKey() {
        try {
            KeyGenerator g = KeyGenerator.getInstance("AES");
            g.init(256);
            return g.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static float[] randomTemplate() {
        Random r = new Random(42);
        float[] t = new float[512];
        for (int i = 0; i < t.length; i++) {
            t[i] = (float) r.nextGaussian();
        }
        return t;
    }

    @Test
    void roundTrip() {
        SecretKey dek = crypto.newDek();
        TemplateCrypto.Encrypted e = crypto.encrypt(dek, template, aad);
        assertThat(crypto.decrypt(dek, e, aad)).containsExactly(template);
    }

    @Test
    void ivIsRandom96BitsAndCiphertextCarries128BitTag() {
        SecretKey dek = crypto.newDek();
        TemplateCrypto.Encrypted a = crypto.encrypt(dek, template, aad);
        TemplateCrypto.Encrypted b = crypto.encrypt(dek, template, aad);
        assertThat(a.iv()).hasSize(12);
        assertThat(a.iv()).isNotEqualTo(b.iv());                 // same plaintext, different IV ...
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext()); // ... so different ciphertext
        assertThat(a.ciphertext()).hasSize(512 * 4 + 16);        // float32 bytes + GCM tag
    }

    @Test
    void tamperedCiphertextIsRejected() {
        SecretKey dek = crypto.newDek();
        TemplateCrypto.Encrypted e = crypto.encrypt(dek, template, aad);
        e.ciphertext()[10] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(dek, e, aad)).hasCauseInstanceOf(AEADBadTagException.class);
    }

    @Test
    void aadBindsTheTemplateToFlightProfileAndModel() {
        // A row copied to another profile (or flight, or model) no longer decrypts.
        SecretKey dek = crypto.newDek();
        TemplateCrypto.Encrypted e = crypto.encrypt(dek, template, aad);
        assertThatThrownBy(() -> crypto.decrypt(dek, e, TemplateCrypto.aad("POC001", "P002", "r50-buffalo_l")))
                .hasCauseInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> crypto.decrypt(dek, e, TemplateCrypto.aad("POC999", "P001", "r50-buffalo_l")))
                .hasCauseInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> crypto.decrypt(dek, e, TemplateCrypto.aad("POC001", "P001", "sface-2021dec")))
                .hasCauseInstanceOf(AEADBadTagException.class);
    }

    @Test
    void anotherFlightsDekCannotDecrypt() {
        TemplateCrypto.Encrypted e = crypto.encrypt(crypto.newDek(), template, aad);
        assertThatThrownBy(() -> crypto.decrypt(crypto.newDek(), e, aad)).hasCauseInstanceOf(AEADBadTagException.class);
    }

    @Test
    void dekWrapRoundTripAndWrappedFormIsNotTheKey() {
        SecretKey dek = crypto.newDek();
        byte[] wrapped = crypto.wrap(dek);
        assertThat(wrapped).isNotEqualTo(dek.getEncoded());
        assertThat(crypto.unwrap(wrapped).getEncoded()).isEqualTo(dek.getEncoded());
    }

    @Test
    void wrappedDekIsUselessWithoutTheKek() {
        byte[] wrapped = crypto.wrap(crypto.newDek());
        TemplateCrypto otherKek = new TemplateCrypto(newAesKey());
        assertThatThrownBy(() -> otherKek.unwrap(wrapped)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutTheWrappedDekTemplatesCannotBeDecrypted() {
        // Crypto-shredding in one line: once the wrapped DEK row is gone (null), there is nothing to unwrap.
        assertThatThrownBy(() -> crypto.unwrap(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aadFormat() {
        assertThat(TemplateCrypto.aad("POC001", "P001", "r50-buffalo_l")).isEqualTo("POC001|P001|r50-buffalo_l");
    }
}
