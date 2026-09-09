package com.incidentops.dto;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.ConfidenceLevel;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.enums.RecommendedAction;
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
public class IncidentContextResponse {
    private UUID incidentId;
    private IncidentStatus status;
    private String repositoryFullName;
    private String branch;
    private String commitSha;
    private ClassificationType classificationType;
    private ConfidenceLevel confidence;
    private String matchedLogExcerpt;
    private RecommendedAction recommendedAction;
    private String playbookDescription;
    private List<String> changedFiles;
    private List<String> timelineSummary;
    private String assignedNotes;
    private Instant createdAt;
    private Instant updatedAt;
}
