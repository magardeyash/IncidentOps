package com.incidentops.entity;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.RecommendedAction;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "playbooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Playbook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_type", nullable = false, unique = true)
    private ClassificationType classificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", nullable = false)
    private RecommendedAction recommendedAction;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;
}
