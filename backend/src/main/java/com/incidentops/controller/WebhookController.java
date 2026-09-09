package com.incidentops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentops.dto.github.GitHubWebhookPayload;
import com.incidentops.service.WebhookIngestionService;
import com.incidentops.service.WebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "GitHub Webhook Ingestion Endpoint")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookIngestionService webhookIngestionService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github")
    @Operation(summary = "Receive GitHub workflow_run webhook events")
    public ResponseEntity<?> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestHeader(value = "X-GitHub-Event", required = false, defaultValue = "workflow_run") String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody String rawBody) {

        log.info("Received GitHub webhook event: {}, deliveryId: {}", eventType, deliveryId);

        // 1. Verify Signature
        if (!signatureVerifier.verifySignature(rawBody, signatureHeader, null)) {
            log.warn("Invalid or missing X-Hub-Signature-256 for deliveryId: {}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid signature");
        }

        // 2. Parse Payload
        GitHubWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, GitHubWebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse GitHub webhook JSON body: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid JSON payload");
        }

        // 3. Process Webhook
        WebhookIngestionService.WebhookProcessingResponse response = webhookIngestionService.processWebhook(deliveryId, eventType, payload);

        return ResponseEntity.ok(response);
    }
}
