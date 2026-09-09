package com.incidentops.service;

import com.incidentops.dto.PostmortemResponse;
import com.incidentops.dto.PostmortemUpdateRequest;
import com.incidentops.entity.*;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.exception.IllegalStateTransitionException;
import com.incidentops.exception.ResourceNotFoundException;
import com.incidentops.repository.IncidentEventRepository;
import com.incidentops.repository.IncidentRepository;
import com.incidentops.repository.PlaybookRepository;
import com.incidentops.repository.PostmortemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostmortemService {

    private static final Logger log = LoggerFactory.getLogger(PostmortemService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final PostmortemRepository postmortemRepository;
    private final PlaybookRepository playbookRepository;

    @Transactional
    public PostmortemResponse generatePostmortem(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + incidentId));

        if (incident.getStatus() != IncidentStatus.RESOLVED && incident.getStatus() != IncidentStatus.IGNORED) {
            throw new IllegalStateTransitionException(
                    "Postmortem generation is only allowed when incident status is RESOLVED or IGNORED. Current status: " + incident.getStatus());
        }

        PipelineRun run = incident.getPipelineRun();
        RepositoryEntity repo = run.getRepository();
        List<IncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);

        // Generate Summary
        String summary = String.format(
                "Postmortem Report for Incident %s\nRepository: %s (Branch: %s, Commit: %s)\nPipeline Run ID: %d\nFinal Status: %s",
                incident.getId(), repo.getFullName(), run.getBranch(), run.getCommitSha(), run.getGithubRunId(), incident.getStatus()
        );

        // Generate Timeline
        String timeline = events.stream()
                .map(e -> String.format("[%s] Event: %s | Detail: %s", e.getCreatedAt(), e.getEventType(), e.getDetail()))
                .collect(Collectors.joining("\n"));

        // Generate Root Cause
        FailureClassification classification = incident.getClassification();
        String rootCause = "Unclassified failure";
        String recommendedFollowUp = "Perform manual investigation of failure logs.";

        if (classification != null) {
            rootCause = String.format(
                    "Classification: %s (Confidence: %s)\nLog Excerpt:\n%s",
                    classification.getType(), classification.getConfidence(), classification.getMatchedLogExcerpt()
            );

            Optional<Playbook> playbookOpt = playbookRepository.findByClassificationType(classification.getType());
            if (playbookOpt.isPresent()) {
                Playbook pb = playbookOpt.get();
                recommendedFollowUp = String.format(
                        "Recommended Action: %s\nPlaybook Guidance: %s",
                        pb.getRecommendedAction(), pb.getDescription()
                );
            }
        }

        Instant now = Instant.now();
        Postmortem postmortem = postmortemRepository.findByIncidentId(incidentId)
                .orElseGet(() -> Postmortem.builder()
                        .incident(incident)
                        .generatedAt(now)
                        .build());

        postmortem.setSummary(summary);
        postmortem.setTimeline(timeline);
        postmortem.setRootCause(rootCause);
        postmortem.setRecommendedFollowUp(recommendedFollowUp);

        Postmortem saved = postmortemRepository.save(postmortem);
        log.info("Generated postmortem for Incident ID: {}", incidentId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PostmortemResponse getPostmortem(UUID incidentId) {
        Postmortem postmortem = postmortemRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem not found for Incident ID: " + incidentId));
        return mapToResponse(postmortem);
    }

    @Transactional
    public PostmortemResponse updatePostmortem(UUID incidentId, PostmortemUpdateRequest request) {
        Postmortem postmortem = postmortemRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Postmortem not found for Incident ID: " + incidentId));

        if (request.getSummary() != null) {
            postmortem.setSummary(request.getSummary());
        }
        if (request.getTimeline() != null) {
            postmortem.setTimeline(request.getTimeline());
        }
        if (request.getRootCause() != null) {
            postmortem.setRootCause(request.getRootCause());
        }
        if (request.getRecommendedFollowUp() != null) {
            postmortem.setRecommendedFollowUp(request.getRecommendedFollowUp());
        }
        postmortem.setEditedAt(Instant.now());

        Postmortem updated = postmortemRepository.save(postmortem);
        log.info("Updated postmortem for Incident ID: {}", incidentId);

        return mapToResponse(updated);
    }

    private PostmortemResponse mapToResponse(Postmortem p) {
        return PostmortemResponse.builder()
                .id(p.getId())
                .incidentId(p.getIncident().getId())
                .summary(p.getSummary())
                .timeline(p.getTimeline())
                .rootCause(p.getRootCause())
                .recommendedFollowUp(p.getRecommendedFollowUp())
                .generatedAt(p.getGeneratedAt())
                .editedAt(p.getEditedAt())
                .build();
    }
}
