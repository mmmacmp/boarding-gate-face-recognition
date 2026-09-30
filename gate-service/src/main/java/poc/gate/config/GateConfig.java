package poc.gate.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import poc.gate.crypto.TemplateCrypto;
import poc.gate.decision.DecisionPolicy;

/**
 * Creates the two plain-Java classes as Spring beans, so they stay free of Spring and easy to unit-test.
 * A @Bean method runs once at startup; its return value is injected wherever the type is needed.
 */
@Configuration
public class GateConfig {

    @Bean
    DecisionPolicy decisionPolicy(GateProperties props) {
        return new DecisionPolicy(props.policy().thresholds(), props.policy().margin());
    }

    /** Loads the KEK from the local PKCS12 keystore (stand-in for a KMS/HSM). Fails startup if it is missing. */
    @Bean
    TemplateCrypto templateCrypto(GateProperties props) throws Exception {
        char[] password = props.kek().password().toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(props.kek().path()))) {
            keyStore.load(in, password);
        }
        return new TemplateCrypto((SecretKey) keyStore.getKey("kek", password));
    }
}
