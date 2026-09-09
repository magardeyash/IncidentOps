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
public class PostmortemResponse {
    private UUID id;
    private UUID incidentId;
    private String summary;
    private String timeline;
    private String rootCause;
    private String recommendedFollowUp;
    private Instant generatedAt;
    private Instant editedAt;
}
