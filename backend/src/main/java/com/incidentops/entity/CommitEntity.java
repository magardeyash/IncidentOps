package com.incidentops.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "commits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sha", nullable = false)
    private String sha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "commit_files", joinColumns = @JoinColumn(name = "commit_id"))
    @Column(name = "file_path")
    @Builder.Default
    private List<String> changedFiles = new ArrayList<>();

    @Column(name = "pushed_at")
    private Instant pushedAt;
}
