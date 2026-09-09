package com.incidentops.service;

import com.incidentops.entity.*;
import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.ConfidenceLevel;
import com.incidentops.enums.EventType;
import com.incidentops.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final IncidentRepository incidentRepository;
    private final FailureClassificationRepository failureClassificationRepository;
    private final PlaybookRepository playbookRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final CommitRepository commitRepository;
    private final GitHubApiClient gitHubApiClient;
    private final ClassificationEngine classificationEngine;

    @Async("classificationTaskExecutor")
    @Transactional
    public CompletableFuture<Void> classifyFailureAsync(UUID incidentId) {
        log.info("Starting async classification for Incident ID: {}", incidentId);

        Optional<Incident> incidentOpt = incidentRepository.findById(incidentId);
        if (incidentOpt.isEmpty()) {
            log.error("Incident not found for classification: {}", incidentId);
            return CompletableFuture.completedFuture(null);
        }

        Incident incident = incidentOpt.get();
        PipelineRun pipelineRun = incident.getPipelineRun();
        RepositoryEntity repo = pipelineRun.getRepository();

        String[] repoParts = repo.getFullName().split("/");
        String owner = repoParts.length > 0 ? repoParts[0] : "";
        String repoName = repoParts.length > 1 ? repoParts[1] : "";

        String logContent = null;
        List<String> changedFiles = new ArrayList<>();

        // 1. Fetch Commit Changed Files
        Optional<CommitEntity> commitOpt = commitRepository.findByShaAndRepositoryId(pipelineRun.getCommitSha(), repo.getId());
        if (commitOpt.isPresent() && commitOpt.get().getChangedFiles() != null) {
            changedFiles.addAll(commitOpt.get().getChangedFiles());
        }

        // 2. Fetch Logs via GitHub API (with Resilience4j)
        try {
            logContent = gitHubApiClient.fetchWorkflowRunLogs(owner, repoName, pipelineRun.getGithubRunId());
        } catch (Exception e) {
            log.warn("Failed to fetch workflow run logs for incident {}: {}", incidentId, e.getMessage());
        }

        ClassificationEngine.ClassificationResult result;
        if (logContent == null) {
            log.warn("Log fetch returned null/failed for incident {}. Fallback to UNKNOWN classification.", incidentId);
            result = ClassificationEngine.ClassificationResult.builder()
                    .type(ClassificationType.UNKNOWN)
                    .confidence(ConfidenceLevel.LOW)
                    .logExcerpt("GitHub API log retrieval failed or logs unavailable.")
                    .build();
        } else {
            result = classificationEngine.classify(logContent, changedFiles);
        }

        // 3. Persist FailureClassification
        Instant now = Instant.now();
        FailureClassification classification = FailureClassification.builder()
                .incident(incident)
                .type(result.getType())
                .confidence(result.getConfidence())
                .matchedLogExcerpt(result.getLogExcerpt())
                .classifiedAt(now)
                .build();

        classification = failureClassificationRepository.save(classification);
        incident.setClassification(classification);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);

        // 4. Lookup Playbook
        String playbookDetail = "";
        Optional<Playbook> playbookOpt = playbookRepository.findByClassificationType(result.getType());
        if (playbookOpt.isPresent()) {
            Playbook playbook = playbookOpt.get();
            playbookDetail = String.format("Recommended Action: %s. Description: %s",
                    playbook.getRecommendedAction(), playbook.getDescription());
        }

        // 5. Save IncidentEvent
        incidentEventRepository.save(IncidentEvent.builder()
                .incident(incident)
                .eventType(EventType.CLASSIFIED)
                .detail(String.format("Incident classified as %s with %s confidence. %s",
                        result.getType(), result.getConfidence(), playbookDetail))
                .createdAt(now)
                .build());

        log.info("Completed async classification for Incident ID {}: {}", incidentId, result.getType());
        return CompletableFuture.completedFuture(null);
    }
}
