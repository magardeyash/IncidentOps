package com.incidentops.repository;

import com.incidentops.entity.Playbook;
import com.incidentops.enums.ClassificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlaybookRepository extends JpaRepository<Playbook, UUID> {
    Optional<Playbook> findByClassificationType(ClassificationType classificationType);
}
