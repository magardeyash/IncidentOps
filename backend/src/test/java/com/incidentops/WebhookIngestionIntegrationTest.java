package com.incidentops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentops.dto.github.*;
import com.incidentops.entity.Incident;
import com.incidentops.entity.PipelineRun;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.enums.PipelineRunStatus;
import com.incidentops.repository.IncidentRepository;
import com.incidentops.repository.PipelineRunRepository;
import com.incidentops.repository.ProcessedDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookIngestionIntegrationTest extends AbstractTestcontainersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PipelineRunRepository pipelineRunRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ProcessedDeliveryRepository processedDeliveryRepository;

    private static final String SECRET = "test-webhook-secret";

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        pipelineRunRepository.deleteAll();
        processedDeliveryRepository.deleteAll();
    }

    private String calculateHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }
        return "sha256=" + sb.toString();
    }

    private GitHubWebhookPayload createPayload(Long runId, String status, String conclusion) {
        return GitHubWebhookPayload.builder()
                .action("completed")
                .repository(RepositoryPayload.builder()
                        .id(1001L)
                        .name("my-app")
                        .fullName("acme/my-app")
                        .build())
                .workflowRun(WorkflowRunPayload.builder()
                        .id(runId)
                        .name("CI Build")
                        .headBranch("main")
                        .headSha("sha-123456")
                        .status(status)
                        .conclusion(conclusion)
                        .createdAt("2026-09-03T10:00:00Z")
                        .updatedAt("2026-09-03T10:05:00Z")
                        .headCommit(HeadCommitPayload.builder()
                                .id("sha-123456")
                                .message("Fix bug in backend")
                                .author(AuthorPayload.builder().name("Dev").email("dev@example.com").build())
                                .modified(List.of("src/App.java"))
                                .build())
                        .build())
                .build();
    }

    @Test
    @DisplayName("Webhook with invalid signature returns 401 Unauthorized")
    void testInvalidSignature() throws Exception {
        String payloadJson = objectMapper.writeValueAsString(createPayload(2001L, "completed", "failure"));

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=invalidhash")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .content(payloadJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Webhook with valid signature and failure conclusion creates Incident in OPEN status")
    void testFailureWebhookCreatesIncident() throws Exception {
        Long runId = 3001L;
        String payloadJson = objectMapper.writeValueAsString(createPayload(runId, "completed", "failure"));
        String signature = calculateHmacSha256(payloadJson, SECRET);
        String deliveryId = UUID.randomUUID().toString();

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", deliveryId)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.incidentCreated").value(true));

        // Verify PipelineRun persisted
        Optional<PipelineRun> runOpt = pipelineRunRepository.findByGithubRunId(runId);
        assertThat(runOpt).isPresent();
        assertThat(runOpt.get().getStatus()).isEqualTo(PipelineRunStatus.FAILURE);

        // Verify Incident created in OPEN status
        Optional<Incident> incidentOpt = incidentRepository.findByPipelineRunId(runOpt.get().getId());
        assertThat(incidentOpt).isPresent();
        assertThat(incidentOpt.get().getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    @DisplayName("Idempotency test: Duplicate delivery ID returns IGNORED_DUPLICATE without creating extra incident")
    void testDuplicateDeliveryIdempotency() throws Exception {
        Long runId = 4001L;
        String payloadJson = objectMapper.writeValueAsString(createPayload(runId, "completed", "failure"));
        String signature = calculateHmacSha256(payloadJson, SECRET);
        String deliveryId = "delivery-duplicate-unique-123";

        // First delivery
        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", deliveryId)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Second delivery with SAME deliveryId
        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", deliveryId)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED_DUPLICATE"));

        // Verify total incidents is 1
        assertThat(incidentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Successful workflow conclusion updates PipelineRun but does NOT create Incident")
    void testSuccessConclusionNoIncident() throws Exception {
        Long runId = 5001L;
        String payloadJson = objectMapper.writeValueAsString(createPayload(runId, "completed", "success"));
        String signature = calculateHmacSha256(payloadJson, SECRET);
        String deliveryId = UUID.randomUUID().toString();

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", deliveryId)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.incidentCreated").value(false));

        Optional<PipelineRun> runOpt = pipelineRunRepository.findByGithubRunId(runId);
        assertThat(runOpt).isPresent();
        assertThat(runOpt.get().getStatus()).isEqualTo(PipelineRunStatus.SUCCESS);

        assertThat(incidentRepository.count()).isZero();
    }
}
