package com.incidentops.dto;

import com.incidentops.enums.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentDetailResponse {
    private UUID id;
    private IncidentStatus status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private String assignedNotes;

    private RepositoryResponse repository;
    private PipelineRunResponse pipelineRun;
    private ClassificationResponse classification;
    private PlaybookResponse playbook;
    private List<IncidentEventResponse> events;
}
