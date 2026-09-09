package com.incidentops.repository;

import com.incidentops.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository extends JpaRepository<RepositoryEntity, UUID> {
    Optional<RepositoryEntity> findByGithubRepoId(Long githubRepoId);
    Optional<RepositoryEntity> findByFullName(String fullName);
}
