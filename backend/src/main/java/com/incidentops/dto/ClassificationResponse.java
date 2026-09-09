package com.incidentops.dto;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.ConfidenceLevel;
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
public class ClassificationResponse {
    private UUID id;
    private ClassificationType type;
    private String matchedLogExcerpt;
    private ConfidenceLevel confidence;
    private Instant classifiedAt;
}
