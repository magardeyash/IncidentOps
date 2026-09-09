package com.incidentops.service;

import com.incidentops.dto.github.GitHubWebhookPayload;
import com.incidentops.dto.github.HeadCommitPayload;
import com.incidentops.dto.github.RepositoryPayload;
import com.incidentops.dto.github.WorkflowRunPayload;
import com.incidentops.entity.*;
import com.incidentops.enums.EventType;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.enums.PipelineRunStatus;
import com.incidentops.repository.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WebhookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionService.class);

    private final ProcessedDeliveryRepository processedDeliveryRepository;
    private final RepositoryRepository repositoryRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final CommitRepository commitRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final GitHubApiClient gitHubApiClient;
    private final ClassificationService classificationService;
    private final String defaultWebhookSecret;

    public WebhookIngestionService(ProcessedDeliveryRepository processedDeliveryRepository,
                                   RepositoryRepository repositoryRepository,
                                   PipelineRunRepository pipelineRunRepository,
                                   CommitRepository commitRepository,
                                   IncidentRepository incidentRepository,
                                   IncidentEventRepository incidentEventRepository,
                                   GitHubApiClient gitHubApiClient,
                                   ClassificationService classificationService,
                                   @Value("${github.webhook.secret}") String defaultWebhookSecret) {
        this.processedDeliveryRepository = processedDeliveryRepository;
        this.repositoryRepository = repositoryRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.commitRepository = commitRepository;
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.gitHubApiClient = gitHubApiClient;
        this.classificationService = classificationService;
        this.defaultWebhookSecret = defaultWebhookSecret;
    }

    @Transactional
    public WebhookProcessingResponse processWebhook(String deliveryId, String eventType, GitHubWebhookPayload payload) {
        // 1. Idempotency Check on Delivery ID
        if (deliveryId != null && !deliveryId.isBlank()) {
            if (processedDeliveryRepository.existsById(deliveryId)) {
                log.info("Duplicate delivery ID ignored: {}", deliveryId);
                return WebhookProcessingResponse.builder()
                        .status("IGNORED_DUPLICATE")
                        .message("Delivery ID already processed")
                        .deliveryId(deliveryId)
                        .build();
            }
            try {
                processedDeliveryRepository.save(ProcessedDelivery.builder()
                        .deliveryId(deliveryId)
                        .processedAt(Instant.now())
                        .build());
            } catch (DataIntegrityViolationException e) {
                log.info("Concurrent delivery ID ignored: {}", deliveryId);
                return WebhookProcessingResponse.builder()
                        .status("IGNORED_DUPLICATE")
                        .message("Delivery ID already processed concurrently")
                        .deliveryId(deliveryId)
                        .build();
            }
        }

        // 2. Handle non-workflow_run events
        if (!"workflow_run".equals(eventType)) {
            return WebhookProcessingResponse.builder()
                    .status("SKIPPED")
                    .message("Event type ignored: " + eventType)
                    .build();
        }

        if (payload == null || payload.getWorkflowRun() == null || payload.getRepository() == null) {
            return WebhookProcessingResponse.builder()
                    .status("ERROR")
                    .message("Invalid payload structure")
                    .build();
        }

        RepositoryPayload repoPayload = payload.getRepository();
        WorkflowRunPayload runPayload = payload.getWorkflowRun();

        // 3. Upsert Repository
        RepositoryEntity repo = repositoryRepository.findByGithubRepoId(repoPayload.getId())
                .orElseGet(() -> repositoryRepository.save(RepositoryEntity.builder()
                        .githubRepoId(repoPayload.getId())
                        .fullName(repoPayload.getFullName())
                        .webhookSecret(defaultWebhookSecret)
                        .installedAt(Instant.now())
                        .active(true)
                        .build()));

        // Update fullName if changed
        if (!repoPayload.getFullName().equals(repo.getFullName())) {
            repo.setFullName(repoPayload.getFullName());
            repositoryRepository.save(repo);
        }

        // 4. Map PipelineRun Status
        PipelineRunStatus runStatus = mapStatus(runPayload.getStatus(), runPayload.getConclusion());

        // 5. Upsert PipelineRun (idempotent on githubRunId)
        PipelineRun pipelineRun = pipelineRunRepository.findByGithubRunId(runPayload.getId())
                .orElseGet(() -> PipelineRun.builder()
                        .repository(repo)
                        .githubRunId(runPayload.getId())
                        .branch(runPayload.getHeadBranch() != null ? runPayload.getHeadBranch() : "main")
                        .commitSha(runPayload.getHeadSha())
                        .status(runStatus)
                        .startedAt(parseInstant(runPayload.getCreatedAt()))
                        .finishedAt(parseInstant(runPayload.getUpdatedAt()))
                        .build());

        pipelineRun.setStatus(runStatus);
        if (runPayload.getUpdatedAt() != null) {
            pipelineRun.setFinishedAt(parseInstant(runPayload.getUpdatedAt()));
        }
        pipelineRun = pipelineRunRepository.save(pipelineRun);

        // 6. Fetch & Persist Commit details
        String sha = runPayload.getHeadSha();
        if (sha != null && !sha.isBlank()) {
            commitRepository.findByShaAndRepositoryId(sha, repo.getId())
                    .orElseGet(() -> fetchAndSaveCommit(repo, sha, runPayload.getHeadCommit()));
        }

        // 7. Process Incident creation on FAILURE
        boolean incidentCreated = false;
        if (PipelineRunStatus.FAILURE.equals(runStatus)) {
            Optional<Incident> existingIncident = incidentRepository.findByPipelineRunId(pipelineRun.getId());
            if (existingIncident.isEmpty()) {
                Instant now = Instant.now();
                Incident incident = incidentRepository.save(Incident.builder()
                        .pipelineRun(pipelineRun)
                        .status(IncidentStatus.OPEN)
                        .version(0L)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

                incidentEventRepository.save(IncidentEvent.builder()
                        .incident(incident)
                        .eventType(EventType.CREATED)
                        .toStatus(IncidentStatus.OPEN)
                        .detail("Incident opened automatically due to GitHub workflow failure (Run #" + runPayload.getId() + ")")
                        .createdAt(now)
                        .build());

                // Trigger Async Classification Job
                classificationService.classifyFailureAsync(incident.getId());
                incidentCreated = true;
                log.info("Created Incident {} for failed pipeline run {}", incident.getId(), pipelineRun.getGithubRunId());
            }
        }

        return WebhookProcessingResponse.builder()
                .status("PROCESSED")
                .message(incidentCreated ? "Incident created and classification triggered" : "Pipeline run updated")
                .pipelineRunId(pipelineRun.getId().toString())
                .incidentCreated(incidentCreated)
                .build();
    }

    private CommitEntity fetchAndSaveCommit(RepositoryEntity repo, String sha, HeadCommitPayload headCommit) {
        String authorName = "Unknown";
        String authorEmail = "unknown@example.com";
        String message = "No commit message";
        List<String> changedFiles = new ArrayList<>();

        if (headCommit != null) {
            if (headCommit.getAuthor() != null) {
                authorName = headCommit.getAuthor().getName();
                authorEmail = headCommit.getAuthor().getEmail();
            }
            if (headCommit.getMessage() != null) {
                message = headCommit.getMessage();
            }
            if (headCommit.getAdded() != null) changedFiles.addAll(headCommit.getAdded());
            if (headCommit.getModified() != null) changedFiles.addAll(headCommit.getModified());
            if (headCommit.getRemoved() != null) changedFiles.addAll(headCommit.getRemoved());
        }

        // If no changed files from payload, query GitHub API via WebClient
        if (changedFiles.isEmpty()) {
            try {
                String[] parts = repo.getFullName().split("/");
                if (parts.length == 2) {
                    GitHubApiClient.GitHubCommitResponse commitResp = gitHubApiClient.fetchCommitDetails(parts[0], parts[1], sha);
                    if (commitResp != null && commitResp.getFiles() != null) {
                        for (GitHubApiClient.GitHubCommitFile file : commitResp.getFiles()) {
                            if (file.getFilename() != null) {
                                changedFiles.add(file.getFilename());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch commit details from GitHub API: {}", e.getMessage());
            }
        }

        return commitRepository.save(CommitEntity.builder()
                .sha(sha)
                .repository(repo)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .message(message)
                .changedFiles(changedFiles)
                .pushedAt(Instant.now())
                .build());
    }

    private PipelineRunStatus mapStatus(String status, String conclusion) {
        if ("failure".equalsIgnoreCase(conclusion)) return PipelineRunStatus.FAILURE;
        if ("success".equalsIgnoreCase(conclusion)) return PipelineRunStatus.SUCCESS;
        if ("cancelled".equalsIgnoreCase(conclusion)) return PipelineRunStatus.CANCELLED;
        if ("in_progress".equalsIgnoreCase(status)) return PipelineRunStatus.IN_PROGRESS;
        return PipelineRunStatus.QUEUED;
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) return Instant.now();
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookProcessingResponse {
        private String status;
        private String message;
        private String deliveryId;
        private String pipelineRunId;
        private boolean incidentCreated;
    }
}
