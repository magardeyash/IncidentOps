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
public class WorkflowRunPayload {
    private Long id;
    private String name;

    @JsonProperty("head_branch")
    private String headBranch;

    @JsonProperty("head_sha")
    private String headSha;

    private String status;
    private String conclusion;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("head_commit")
    private HeadCommitPayload headCommit;
}
