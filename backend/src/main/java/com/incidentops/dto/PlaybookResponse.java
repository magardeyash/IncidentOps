package com.incidentops.dto;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.RecommendedAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybookResponse {
    private UUID id;
    private ClassificationType classificationType;
    private RecommendedAction recommendedAction;
    private String description;
}
