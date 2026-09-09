package com.incidentops.repository;

import com.incidentops.entity.CommitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommitRepository extends JpaRepository<CommitEntity, UUID> {
    Optional<CommitEntity> findByShaAndRepositoryId(String sha, UUID repositoryId);
    Optional<CommitEntity> findFirstBySha(String sha);
}
