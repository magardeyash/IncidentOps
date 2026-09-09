package com.incidentops.entity;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.ConfidenceLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "failure_classifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ClassificationType type;

    @Column(name = "matched_log_excerpt", columnDefinition = "TEXT")
    private String matchedLogExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false)
    private ConfidenceLevel confidence;

    @Column(name = "classified_at", nullable = false)
    private Instant classifiedAt;
}
