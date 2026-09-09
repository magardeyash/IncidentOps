package com.incidentops.service;

import com.incidentops.dto.*;
import com.incidentops.entity.*;
import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.EventType;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.enums.RecommendedAction;
import com.incidentops.exception.IllegalStateTransitionException;
import com.incidentops.exception.ResourceNotFoundException;
import com.incidentops.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final PlaybookRepository playbookRepository;
    private final CommitRepository commitRepository;

    @Transactional(readOnly = true)
    public Page<IncidentDetailResponse> getIncidents(IncidentStatus status,
                                                     ClassificationType classificationType,
                                                     String repositoryFullName,
                                                     Instant startDate,
                                                     Instant endDate,
                                                     Pageable pageable) {
        Specification<Incident> spec = IncidentSpecification.withFilters(status, classificationType, repositoryFullName, startDate, endDate);
        return incidentRepository.findAll(spec, pageable).map(this::mapToDetailResponse);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getIncidentById(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + id));
        return mapToDetailResponse(incident);
    }

    @Transactional(readOnly = true)
    public IncidentContextResponse getIncidentContext(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + id));

        PipelineRun run = incident.getPipelineRun();
        RepositoryEntity repo = run.getRepository();
        FailureClassification classification = incident.getClassification();

        List<String> changedFiles = List.of();
        Optional<CommitEntity> commitOpt = commitRepository.findByShaAndRepositoryId(run.getCommitSha(), repo.getId());
        if (commitOpt.isPresent() && commitOpt.get().getChangedFiles() != null) {
            changedFiles = commitOpt.get().getChangedFiles();
        }

        RecommendedAction action = null;
        String playbookDesc = null;
        if (classification != null) {
            Optional<Playbook> pbOpt = playbookRepository.findByClassificationType(classification.getType());
            if (pbOpt.isPresent()) {
                action = pbOpt.get().getRecommendedAction();
                playbookDesc = pbOpt.get().getDescription();
            }
        }

        List<String> timeline = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(id)
                .stream()
                .map(e -> String.format("[%s] %s: %s", e.getCreatedAt(), e.getEventType(), e.getDetail()))
                .toList();

        return IncidentContextResponse.builder()
                .incidentId(incident.getId())
                .status(incident.getStatus())
                .repositoryFullName(repo.getFullName())
                .branch(run.getBranch())
                .commitSha(run.getCommitSha())
                .classificationType(classification != null ? classification.getType() : null)
                .confidence(classification != null ? classification.getConfidence() : null)
                .matchedLogExcerpt(classification != null ? classification.getMatchedLogExcerpt() : null)
                .recommendedAction(action)
                .playbookDescription(playbookDesc)
                .changedFiles(changedFiles)
                .timelineSummary(timeline)
                .assignedNotes(incident.getAssignedNotes())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }

    @Transactional
    public IncidentDetailResponse updateStatus(UUID id, IncidentStatus newStatus) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + id));

        IncidentStatus currentStatus = incident.getStatus();
        validateStateTransition(currentStatus, newStatus);

        incident.setStatus(newStatus);
        Instant now = Instant.now();
        incident.setUpdatedAt(now);

        Incident updatedIncident = incidentRepository.save(incident);

        incidentEventRepository.save(IncidentEvent.builder()
                .incident(updatedIncident)
                .eventType(EventType.STATE_CHANGED)
                .fromStatus(currentStatus)
                .toStatus(newStatus)
                .detail(String.format("Incident state changed from %s to %s", currentStatus, newStatus))
                .createdAt(now)
                .build());

        log.info("Incident {} transitioned from {} to {}", id, currentStatus, newStatus);
        return mapToDetailResponse(updatedIncident);
    }

    @Transactional
    public IncidentDetailResponse addNote(UUID id, String note) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + id));

        String existingNotes = incident.getAssignedNotes();
        String updatedNotes = (existingNotes == null || existingNotes.isBlank()) ? note : existingNotes + "\n---\n" + note;

        incident.setAssignedNotes(updatedNotes);
        Instant now = Instant.now();
        incident.setUpdatedAt(now);

        Incident updatedIncident = incidentRepository.save(incident);

        incidentEventRepository.save(IncidentEvent.builder()
                .incident(updatedIncident)
                .eventType(EventType.NOTE_ADDED)
                .detail(note)
                .createdAt(now)
                .build());

        log.info("Added note to Incident {}", id);
        return mapToDetailResponse(updatedIncident);
    }

    private void validateStateTransition(IncidentStatus currentStatus, IncidentStatus newStatus) {
        if (currentStatus == newStatus) {
            return;
        }

        boolean legal = switch (currentStatus) {
            case OPEN -> newStatus == IncidentStatus.INVESTIGATING;
            case INVESTIGATING -> newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.IGNORED;
            case RESOLVED, IGNORED -> false;
        };

        if (!legal) {
            throw new IllegalStateTransitionException(
                    String.format("Illegal state transition attempt from %s to %s for incident", currentStatus, newStatus));
        }
    }

    private IncidentDetailResponse mapToDetailResponse(Incident incident) {
        PipelineRun run = incident.getPipelineRun();
        RepositoryEntity repo = run.getRepository();
        FailureClassification classification = incident.getClassification();

        PlaybookResponse playbookResponse = null;
        if (classification != null) {
            Optional<Playbook> playbookOpt = playbookRepository.findByClassificationType(classification.getType());
            if (playbookOpt.isPresent()) {
                Playbook pb = playbookOpt.get();
                playbookResponse = PlaybookResponse.builder()
                        .id(pb.getId())
                        .classificationType(pb.getClassificationType())
                        .recommendedAction(pb.getRecommendedAction())
                        .description(pb.getDescription())
                        .build();
            }
        }

        List<IncidentEventResponse> eventResponses = incidentEventRepository
                .findByIncidentIdOrderByCreatedAtAsc(incident.getId())
                .stream()
                .map(e -> IncidentEventResponse.builder()
                        .id(e.getId())
                        .eventType(e.getEventType())
                        .fromStatus(e.getFromStatus())
                        .toStatus(e.getToStatus())
                        .detail(e.getDetail())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();

        return IncidentDetailResponse.builder()
                .id(incident.getId())
                .status(incident.getStatus())
                .version(incident.getVersion())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .assignedNotes(incident.getAssignedNotes())
                .repository(RepositoryResponse.builder()
                        .id(repo.getId())
                        .githubRepoId(repo.getGithubRepoId())
                        .fullName(repo.getFullName())
                        .installedAt(repo.getInstalledAt())
                        .active(repo.isActive())
                        .build())
                .pipelineRun(PipelineRunResponse.builder()
                        .id(run.getId())
                        .githubRunId(run.getGithubRunId())
                        .branch(run.getBranch())
                        .commitSha(run.getCommitSha())
                        .status(run.getStatus())
                        .startedAt(run.getStartedAt())
                        .finishedAt(run.getFinishedAt())
                        .build())
                .classification(classification != null ? ClassificationResponse.builder()
                        .id(classification.getId())
                        .type(classification.getType())
                        .matchedLogExcerpt(classification.getMatchedLogExcerpt())
                        .confidence(classification.getConfidence())
                        .classifiedAt(classification.getClassifiedAt())
                        .build() : null)
                .playbook(playbookResponse)
                .events(eventResponses)
                .build();
    }
}
