package com.incidentops.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "postmortems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Postmortem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "timeline", columnDefinition = "TEXT")
    private String timeline;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "recommended_follow_up", columnDefinition = "TEXT")
    private String recommendedFollowUp;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "edited_at")
    private Instant editedAt;
}
