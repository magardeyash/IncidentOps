package com.incidentops.dto;

import com.incidentops.enums.PipelineRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRunResponse {
    private UUID id;
    private Long githubRunId;
    private String branch;
    private String commitSha;
    private PipelineRunStatus status;
    private Instant startedAt;
    private Instant finishedAt;
}
