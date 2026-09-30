package poc.gate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import poc.gate.events.GateDecisionService;
import poc.gate.events.GateEvent;
import poc.gate.gallery.ConsentService;
import poc.gate.gallery.FlightRepository;
import poc.gate.gallery.GalleryEntryRepository;
import poc.gate.gallery.GalleryService;

/**
 * Base for integration tests: the real Spring context against the compose Postgres ("gate_test" database), with
 * WireMock playing model-service, mock-idp and dcs-stub (all on one port, as the real mocks share one).
 * Every test uses fresh random flight and profile ids, so tests never interfere and need no cleanup.
 * Embeddings are tiny 4-d unit vectors: WireMock maps each fake "image" (a unique byte string) to one of them.
 */
@SpringBootTest
public abstract class AbstractGateIT {

    protected static final String MODEL = "r50-buffalo_l";
    protected static final WireMockServer wiremock = new WireMockServer(options().dynamicPort());
    private static final char[] KEK_PASSWORD = "integration-tests-only".toCharArray();
    private static final Path KEYSTORE = createKeystore();

    static {
        wiremock.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/gate_test");
        registry.add("gate.model-service.url", wiremock::baseUrl);
        registry.add("gate.identity.url", wiremock::baseUrl);
        registry.add("gate.dcs.url", wiremock::baseUrl);
        registry.add("gate.kek.path", KEYSTORE::toString);
        registry.add("gate.kek.password", () -> new String(KEK_PASSWORD));
        registry.add("gate.retention.grace", () -> "0s");
        registry.add("gate.retention.purge-interval", () -> "1h"); // tests call PurgeJob.run() themselves
        registry.add("resilience4j.circuitbreaker.instances.identity.wait-duration-in-open-state", () -> "1s");
    }

    @Autowired
    protected GateDecisionService gate;
    @Autowired
    protected GalleryService gallery;
    @Autowired
    protected ConsentService consents;
    @Autowired
    protected FlightRepository flights;
    @Autowired
    protected GalleryEntryRepository entries;
    @Autowired
    protected CircuitBreakerRegistry breakers;

    @BeforeEach
    void resetFakes() {
        wiremock.resetAll();
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset); // breakers are shared by the whole context
    }

    private static Path createKeystore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            keyStore.setEntry("kek", new KeyStore.SecretKeyEntry(generator.generateKey()),
                    new KeyStore.PasswordProtection(KEK_PASSWORD));
            Path path = Files.createTempFile("it-kek", ".p12");
            try (OutputStream out = Files.newOutputStream(path)) {
                keyStore.store(out, KEK_PASSWORD);
            }
            return path;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6);
    }

    protected static float[] unit(double... v) {
        double norm = Math.sqrt(Arrays.stream(v).map(x -> x * x).sum());
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    private static String embedJson(float[]... faces) {
        String list = Arrays.stream(faces).map(f -> "{\"bbox\":[0,0,200,200],\"det_score\":0.9,\"embedding\":"
                + IntStream.range(0, f.length).mapToObj(i -> Float.toString(f[i]))
                        .collect(Collectors.joining(",", "[", "]")) + "}")
                .collect(Collectors.joining(","));
        return "{\"model_id\":\"" + MODEL + "\",\"dim\":4,\"faces\":[" + list + "]}";
    }

    /** A fake capture: unique bytes that the fake model-service turns into the given face(s). */
    protected static byte[] probe(float[]... faces) {
        byte[] image = unique("probe-").getBytes(StandardCharsets.UTF_8);
        stubEmbed(image, okJson(embedJson(faces)));
        return image;
    }

    protected static void stubEmbed(byte[] image, ResponseDefinitionBuilder response) {
        wiremock.stubFor(post("/v1/embed").withRequestBody(binaryEqualTo(image)).willReturn(response));
    }

    protected static void stubPassenger(String profileId, float[] template) {
        byte[] reference = ("ref-" + profileId).getBytes(StandardCharsets.UTF_8);
        wiremock.stubFor(get("/v1/profiles/" + profileId + "/reference-image")
                .willReturn(aResponse().withBody(reference)));
        stubEmbed(reference, okJson(embedJson(template)));
    }

    protected static void stubManifest(String flightId, Iterable<String> profileIds) {
        String passengers = String.join(",", java.util.stream.StreamSupport.stream(profileIds.spliterator(), false)
                .map(p -> "{\"profile_id\":\"" + p + "\"}").toList());
        wiremock.stubFor(get("/v1/flights/" + flightId + "/manifest").willReturn(okJson(
                "{\"flight_id\":\"" + flightId + "\",\"departure_time\":\"2030-01-01T10:00:00Z\",\"passengers\":["
                        + passengers + "]}")));
    }

    protected static void stubVerify(ResponseDefinitionBuilder response) {
        wiremock.stubFor(post("/v1/verify").willReturn(response));
    }

    protected static ResponseDefinitionBuilder verifyResult(String result) {
        return okJson("{\"result\":\"" + result + "\"}");
    }

    /** Consented passengers on a manifest, gallery built, identity provider answering VALID. */
    protected String boardingFlight(Map<String, float[]> passengers) {
        String flightId = unique("IT");
        passengers.forEach((profileId, template) -> {
            consents.grant(profileId);
            stubPassenger(profileId, template);
        });
        stubManifest(flightId, passengers.keySet());
        stubVerify(verifyResult("VALID"));
        gallery.build(flightId);
        return flightId;
    }

    protected static Map<String, float[]> passengers(Object... idAndTemplate) {
        Map<String, float[]> map = new LinkedHashMap<>();
        for (int i = 0; i < idAndTemplate.length; i += 2) {
            map.put((String) idAndTemplate[i], (float[]) idAndTemplate[i + 1]);
        }
        return map;
    }

    protected GateEvent event(String gateId, String flightId, byte[] image) {
        return gate.handle(gateId, flightId, UUID.randomUUID().toString(), image);
    }
}
