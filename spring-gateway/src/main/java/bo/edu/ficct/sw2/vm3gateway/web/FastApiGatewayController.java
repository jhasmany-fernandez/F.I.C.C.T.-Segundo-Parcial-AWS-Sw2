package bo.edu.ficct.sw2.vm3gateway.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class FastApiGatewayController {

    private final RestTemplate restTemplate;
    private final String aiServiceBaseUrl;

    public FastApiGatewayController(
            RestTemplate restTemplate,
            @Value("${ai.service.base-url}") String aiServiceBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.aiServiceBaseUrl = aiServiceBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "service", "vm3-spring-gateway",
                "status", "ok"
        );
    }

    @GetMapping("/gateway/status")
    public ResponseEntity<String> gatewayStatus() {
        return forwardJsonGet("/health");
    }

    @PostMapping("/gateway/ai/clasificar")
    public ResponseEntity<String> classifyIncident(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/ai/clasificar-incidente", payload);
    }

    @PostMapping("/gateway/dynamodb/evidencia")
    public ResponseEntity<String> dynamodbEvidence(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/dynamodb/evidencia", payload);
    }

    @PostMapping("/gateway/blockchain/registrar")
    public ResponseEntity<String> blockchainRegister(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/blockchain/registrar", payload);
    }

    @PostMapping("/gateway/n8n/webhook")
    public ResponseEntity<String> n8nWebhook(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/automation/n8n/webhook", payload);
    }

    @PostMapping(path = "/gateway/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> speechToText(
            @RequestParam @NotBlank String emergencia_id,
            @RequestParam MultipartFile audio
    ) throws IOException {
        return forwardMultipart("/ai/speech-to-text", emergencia_id, "audio", audio);
    }

    @PostMapping(path = "/gateway/deep-learning/vision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> deepLearningVision(
            @RequestParam @NotBlank String emergencia_id,
            @RequestParam MultipartFile imagen
    ) throws IOException {
        return forwardMultipart("/ai/deep-learning/vision", emergencia_id, "imagen", imagen);
    }

    @PostMapping(path = "/gateway/s3/upload-evidencia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> s3UploadEvidence(
            @RequestParam @NotBlank String emergencia_id,
            @RequestParam MultipartFile archivo
    ) throws IOException {
        return forwardMultipart("/aws-s3/upload-evidencia", emergencia_id, "archivo", archivo);
    }

    private ResponseEntity<String> forwardJsonGet(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        return exchange(path, HttpMethod.GET, new HttpEntity<>(headers));
    }

    private ResponseEntity<String> forwardJsonPost(String path, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
        return exchange(path, HttpMethod.POST, new HttpEntity<>(payload, headers));
    }

    private ResponseEntity<String> forwardMultipart(
            String path,
            String emergenciaId,
            String fileField,
            MultipartFile file
    ) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("emergencia_id", emergenciaId);
        body.add(fileField, multipartResource(file));

        return exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers));
    }

    private ByteArrayResource multipartResource(MultipartFile file) throws IOException {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, HttpEntity<?> request) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    aiServiceBaseUrl + path,
                    method,
                    request,
                    String.class
            );
            return ResponseEntity
                    .status(response.getStatusCode())
                    .headers(filterResponseHeaders(response.getHeaders()))
                    .body(response.getBody());
        } catch (HttpStatusCodeException exception) {
            return ResponseEntity
                    .status(exception.getStatusCode())
                    .headers(filterResponseHeaders(exception.getResponseHeaders()))
                    .body(exception.getResponseBodyAsString());
        }
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        if (source != null && source.getContentType() != null) {
            headers.setContentType(source.getContentType());
        } else {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return headers;
    }
}
