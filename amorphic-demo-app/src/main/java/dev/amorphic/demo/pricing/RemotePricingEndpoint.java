package dev.amorphic.demo.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** `remote` binding: an HTTP call to GET {url}/convert. */
class RemotePricingEndpoint implements PricingEndpoint {

    private final RestClient client;

    RemotePricingEndpoint(String url) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        try {
            JsonNode body = client.get()
                    .uri(b -> b.path("/convert")
                            .queryParam("amount", amount.toPlainString())
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.hasNonNull("amount")) {
                throw new IllegalStateException("response has no amount");
            }
            return new BigDecimal(body.get("amount").asText());
        } catch (HttpClientErrorException e) {
            JsonNode error = e.getResponseBodyAs(JsonNode.class);
            throw new PricingRejectedException(error != null && error.hasNonNull("error")
                    ? error.get("error").asText() : e.getStatusText());
        }
    }
}
