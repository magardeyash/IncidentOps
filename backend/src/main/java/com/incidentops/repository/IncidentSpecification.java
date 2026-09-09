package com.incidentops.repository;

import com.incidentops.entity.FailureClassification;
import com.incidentops.entity.Incident;
import com.incidentops.entity.PipelineRun;
import com.incidentops.entity.RepositoryEntity;
import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.IncidentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IncidentSpecification {

    public static Specification<Incident> withFilters(IncidentStatus status,
                                                      ClassificationType classificationType,
                                                      String repositoryFullName,
                                                      Instant startDate,
                                                      Instant endDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (classificationType != null) {
                Join<Incident, FailureClassification> classificationJoin = root.join("classification", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(classificationJoin.get("type"), classificationType));
            }

            if (repositoryFullName != null && !repositoryFullName.isBlank()) {
                Join<Incident, PipelineRun> pipelineJoin = root.join("pipelineRun", JoinType.INNER);
                Join<PipelineRun, RepositoryEntity> repoJoin = pipelineJoin.join("repository", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(repoJoin.get("fullName")), repositoryFullName.toLowerCase()));
            }

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
