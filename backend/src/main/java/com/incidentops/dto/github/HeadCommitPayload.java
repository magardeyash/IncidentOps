package com.incidentops.dto.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeadCommitPayload {
    private String id;
    private String message;
    private String timestamp;
    private AuthorPayload author;
    private CommitterPayload committer;
    private List<String> added;
    private List<String> removed;
    private List<String> modified;
}
