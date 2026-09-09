package com.incidentops.dto;

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
public class RepositoryResponse {
    private UUID id;
    private Long githubRepoId;
    private String fullName;
    private Instant installedAt;
    private boolean active;
}
