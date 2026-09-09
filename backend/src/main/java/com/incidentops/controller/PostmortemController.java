package com.incidentops.controller;

import com.incidentops.dto.PostmortemResponse;
import com.incidentops.dto.PostmortemUpdateRequest;
import com.incidentops.service.PostmortemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/incidents/{id}/postmortem")
@RequiredArgsConstructor
@Tag(name = "Postmortems", description = "Incident Postmortem Generation & Editing Endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PostmortemController {

    private final PostmortemService postmortemService;

    @PostMapping
    @Operation(summary = "Generate postmortem report for a RESOLVED or IGNORED incident")
    public ResponseEntity<PostmortemResponse> generatePostmortem(@PathVariable UUID id) {
        PostmortemResponse response = postmortemService.generatePostmortem(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Retrieve generated postmortem report for an incident")
    public ResponseEntity<PostmortemResponse> getPostmortem(@PathVariable UUID id) {
        PostmortemResponse response = postmortemService.getPostmortem(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @Operation(summary = "Edit postmortem summary, root cause, or recommended follow-up")
    public ResponseEntity<PostmortemResponse> updatePostmortem(
            @PathVariable UUID id,
            @RequestBody PostmortemUpdateRequest request) {
        PostmortemResponse response = postmortemService.updatePostmortem(id, request);
        return ResponseEntity.ok(response);
    }
}
