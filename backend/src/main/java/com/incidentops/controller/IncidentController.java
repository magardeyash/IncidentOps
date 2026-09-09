package com.incidentops.controller;

import com.incidentops.dto.AddNoteRequest;
import com.incidentops.dto.IncidentContextResponse;
import com.incidentops.dto.IncidentDetailResponse;
import com.incidentops.dto.StatusUpdateRequest;
import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents", description = "Incident Lifecycle Management Endpoints")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    @Operation(summary = "List incidents with pagination and filtering")
    public ResponseEntity<Page<IncidentDetailResponse>> listIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) ClassificationType classificationType,
            @RequestParam(required = false) String repository,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction direction = (sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        Page<IncidentDetailResponse> result = incidentService.getIncidents(status, classificationType, repository, startDate, endDate, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information for a specific incident")
    public ResponseEntity<IncidentDetailResponse> getIncident(@PathVariable UUID id) {
        IncidentDetailResponse incident = incidentService.getIncidentById(id);
        return ResponseEntity.ok(incident);
    }

    @GetMapping("/{id}/context")
    @Operation(summary = "Get structured context bundle for AI Advisor consumption")
    public ResponseEntity<IncidentContextResponse> getIncidentContext(@PathVariable UUID id) {
        IncidentContextResponse context = incidentService.getIncidentContext(id);
        return ResponseEntity.ok(context);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition incident status enforcing state machine rules and optimistic locking")
    public ResponseEntity<IncidentDetailResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {
        IncidentDetailResponse updated = incidentService.updateStatus(id, request.getNewStatus());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Add a manual note to an incident")
    public ResponseEntity<IncidentDetailResponse> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody AddNoteRequest request) {
        IncidentDetailResponse updated = incidentService.addNote(id, request.getNote());
        return ResponseEntity.ok(updated);
    }
}
