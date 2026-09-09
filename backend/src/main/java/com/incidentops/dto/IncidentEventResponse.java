package com.incidentops.dto;

import com.incidentops.enums.EventType;
import com.incidentops.enums.IncidentStatus;
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
public class IncidentEventResponse {
    private UUID id;
    private EventType eventType;
    private IncidentStatus fromStatus;
    private IncidentStatus toStatus;
    private String detail;
    private Instant createdAt;
}
