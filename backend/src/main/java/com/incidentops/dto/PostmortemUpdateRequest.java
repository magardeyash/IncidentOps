package com.incidentops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostmortemUpdateRequest {
    private String summary;
    private String timeline;
    private String rootCause;
    private String recommendedFollowUp;
}
