package bo.edu.ficct.sw2.vm3gateway.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Gateway Core", description = "Public Spring Boot gateway endpoints that proxy the internal FastAPI service")
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
    @Operation(summary = "Gateway health", description = "Returns the public health status of the Spring Boot gateway on VM3")
    public Map<String, String> health() {
        return Map.of(
                "service", "vm3-spring-gateway",
                "status", "ok"
        );
    }

    @GetMapping("/gateway/status")
    @Operation(summary = "FastAPI upstream status", description = "Checks the internal FastAPI core service running behind the Spring gateway")
    public ResponseEntity<String> gatewayStatus() {
        return forwardJsonGet("/health");
    }

    @PostMapping("/gateway/ai/clasificar")
    @Operation(summary = "Classify incident", description = "Proxies incident classification requests to the internal FastAPI AI service")
    public ResponseEntity<String> classifyIncident(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/ai/clasificar-incidente", payload);
    }

    @PostMapping("/gateway/dynamodb/evidencia")
    @Operation(summary = "Register evidence metadata", description = "Proxies evidence metadata persistence to the internal DynamoDB-simulated endpoint")
    public ResponseEntity<String> dynamodbEvidence(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/dynamodb/evidencia", payload);
    }

    @PostMapping("/gateway/blockchain/registrar")
    @Operation(summary = "Register blockchain event", description = "Proxies blockchain audit registration to the internal FastAPI core")
    public ResponseEntity<String> blockchainRegister(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/blockchain/registrar", payload);
    }

    @PostMapping("/gateway/n8n/webhook")
    @Operation(summary = "Trigger n8n automation", description = "Proxies automation webhook requests to the internal FastAPI service")
    public ResponseEntity<String> n8nWebhook(@RequestBody Map<String, Object> payload) {
        return forwardJsonPost("/automation/n8n/webhook", payload);
    }

    @PostMapping(path = "/gateway/speech-to-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Speech to text", description = "Uploads an audio file through the gateway and proxies it to the internal speech-to-text service")
    public ResponseEntity<String> speechToText(
            @Parameter(description = "Emergency identifier associated with the uploaded audio")
            @RequestParam @NotBlank String emergencia_id,
            @Parameter(description = "Audio file to transcribe")
            @RequestParam MultipartFile audio
    ) throws IOException {
        return forwardMultipart("/ai/speech-to-text", emergencia_id, "audio", audio);
    }

    @PostMapping(path = "/gateway/deep-learning/vision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Vision analysis", description = "Uploads an image through the gateway and proxies it to the internal deep learning vision service")
    public ResponseEntity<String> deepLearningVision(
            @Parameter(description = "Emergency identifier associated with the uploaded image")
            @RequestParam @NotBlank String emergencia_id,
            @Parameter(description = "Image file to analyze")
            @RequestParam MultipartFile imagen
    ) throws IOException {
        return forwardMultipart("/ai/deep-learning/vision", emergencia_id, "imagen", imagen);
    }

    @PostMapping(path = "/gateway/s3/upload-evidencia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload evidence", description = "Uploads an evidence file through the gateway and proxies it to the internal S3-simulated endpoint")
    public ResponseEntity<String> s3UploadEvidence(
            @Parameter(description = "Emergency identifier associated with the uploaded evidence")
            @RequestParam @NotBlank String emergencia_id,
            @Parameter(description = "Evidence file to upload")
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
