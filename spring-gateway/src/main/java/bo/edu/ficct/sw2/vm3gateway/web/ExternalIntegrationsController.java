package bo.edu.ficct.sw2.vm3gateway.web;

import bo.edu.ficct.sw2.vm3gateway.config.IntegrationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ExternalIntegrationsController {

    private final WebClient webClient;
    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public ExternalIntegrationsController(
            WebClient integrationsWebClient,
            IntegrationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.webClient = integrationsWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/integrations/vm1/health")
    public ResponseEntity<Map<String, Object>> vm1Health() {
        return forwardGet("VM1-AZURE", properties.getVm1BaseUrl() + "/nest/health", false);
    }

    @GetMapping("/integrations/vm2/health")
    public ResponseEntity<Map<String, Object>> vm2Health() {
        return forwardGet("VM2-GCP", properties.getVm2BaseUrl() + "/api/health", false);
    }

    @GetMapping("/integrations/vm2/emergencias")
    public ResponseEntity<Map<String, Object>> vm2Emergencias() {
        return forwardGet("VM2-GCP", properties.getVm2BaseUrl() + "/api/emergencias", true);
    }

    private ResponseEntity<Map<String, Object>> forwardGet(String vm, String url, boolean includeVm2Token) {
        try {
            WebClient.RequestHeadersSpec<?> request = webClient
                    .get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON);

            if (includeVm2Token && !properties.getVm2JwtToken().isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getVm2JwtToken());
            }

            ResponseEntity<String> upstream = request
                    .exchangeToMono(response -> response.toEntity(String.class))
                    .timeout(Duration.ofSeconds(12))
                    .block();

            if (upstream == null) {
                return error(vm, url, "Upstream response was empty");
            }

            return ResponseEntity
                    .status(upstream.getStatusCode())
                    .body(success(vm, url, upstream.getStatusCode(), upstream.getBody()));
        } catch (Exception exception) {
            return error(vm, url, exception.getMessage());
        }
    }

    private Map<String, Object> success(String vm, String url, HttpStatusCode upstreamStatus, String body) {
        Map<String, Object> response = base(vm, url);
        response.put("status", upstreamStatus.is2xxSuccessful() ? "ok" : "error");
        response.put("upstreamStatus", upstreamStatus.value());
        response.put("data", parseBody(body));
        return response;
    }

    private Object parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (JsonProcessingException exception) {
            return body;
        }
    }

    private ResponseEntity<Map<String, Object>> error(String vm, String url, String message) {
        Map<String, Object> response = base(vm, url);
        response.put("status", "error");
        response.put("message", message == null || message.isBlank() ? "Unknown integration error" : message);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    private Map<String, Object> base(String vm, String url) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vm", vm);
        response.put("method", "GET");
        response.put("url", url);
        return response;
    }
}
