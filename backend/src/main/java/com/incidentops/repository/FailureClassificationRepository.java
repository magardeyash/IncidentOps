package com.incidentops.repository;

import com.incidentops.entity.FailureClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FailureClassificationRepository extends JpaRepository<FailureClassification, UUID> {
    Optional<FailureClassification> findByIncidentId(UUID incidentId);
}
