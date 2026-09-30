package poc.gate.clients;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import poc.gate.config.GateProperties;

/** Builds a RestClient (like an httpx.Client) with a base URL and hard connect/read timeouts. */
final class RestClients {

    private RestClients() {
    }

    static RestClient create(RestClient.Builder builder, GateProperties.Endpoint endpoint) {
        // Spring Boot hands every injection point a fresh Builder, pre-configured with the app's JSON settings,
        // so each client can set its own timeouts without affecting the others.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(endpoint.connectTimeout());
        factory.setReadTimeout(endpoint.readTimeout());
        return builder.baseUrl(endpoint.url()).requestFactory(factory).build();
    }
}
