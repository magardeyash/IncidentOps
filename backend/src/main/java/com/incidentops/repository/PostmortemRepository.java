package com.incidentops.repository;

import com.incidentops.entity.Postmortem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostmortemRepository extends JpaRepository<Postmortem, UUID> {
    Optional<Postmortem> findByIncidentId(UUID incidentId);
}
