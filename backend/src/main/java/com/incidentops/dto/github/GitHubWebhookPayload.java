package com.incidentops.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubWebhookPayload {
    private String action;

    @JsonProperty("workflow_run")
    private WorkflowRunPayload workflowRun;

    private RepositoryPayload repository;
}
