package com.incidentops;

import com.incidentops.entity.*;
import com.incidentops.enums.*;
import com.incidentops.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositorySchemaTest extends AbstractTestcontainersTest {

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PipelineRunRepository pipelineRunRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private FailureClassificationRepository failureClassificationRepository;

    @Autowired
    private PlaybookRepository playbookRepository;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Test
    @DisplayName("Verify Playbook table is seeded via Flyway")
    void testPlaybookSeeding() {
        List<Playbook> playbooks = playbookRepository.findAll();
        assertThat(playbooks).hasSize(6);

        Optional<Playbook> buildErrorPlaybook = playbookRepository.findByClassificationType(ClassificationType.BUILD_ERROR);
        assertThat(buildErrorPlaybook).isPresent();
        assertThat(buildErrorPlaybook.get().getRecommendedAction()).isEqualTo(RecommendedAction.MANUAL_FIX);

        Optional<Playbook> testFailurePlaybook = playbookRepository.findByClassificationType(ClassificationType.TEST_FAILURE);
        assertThat(testFailurePlaybook).isPresent();
        assertThat(testFailurePlaybook.get().getRecommendedAction()).isEqualTo(RecommendedAction.RETRY);
    }

    @Test
    @DisplayName("Verify Repository, PipelineRun, Commit, Incident entities persist correctly")
    void testEntityPersistence() {
        // Create Repository
        RepositoryEntity repo = repositoryRepository.save(RepositoryEntity.builder()
                .githubRepoId(123456L)
                .fullName("owner/repo")
                .webhookSecret("secret123")
                .installedAt(Instant.now())
                .active(true)
                .build());
        assertThat(repo.getId()).isNotNull();

        // Create Commit
        CommitEntity commit = commitRepository.save(CommitEntity.builder()
                .sha("abc123def456")
                .repository(repo)
                .authorName("Jane Developer")
                .authorEmail("jane@example.com")
                .message("Fix build pipeline issue")
                .changedFiles(List.of("src/Main.java", "pom.xml"))
                .pushedAt(Instant.now())
                .build());
        assertThat(commit.getId()).isNotNull();
        assertThat(commit.getChangedFiles()).containsExactly("src/Main.java", "pom.xml");

        // Create PipelineRun
        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .repository(repo)
                .githubRunId(987654L)
                .branch("main")
                .commitSha(commit.getSha())
                .status(PipelineRunStatus.FAILURE)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build());
        assertThat(run.getId()).isNotNull();

        // Create Incident
        Incident incident = incidentRepository.save(Incident.builder()
                .pipelineRun(run)
                .status(IncidentStatus.OPEN)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getVersion()).isNotNull();

        // Create IncidentEvent
        IncidentEvent event = incidentEventRepository.save(IncidentEvent.builder()
                .incident(incident)
                .eventType(EventType.CREATED)
                .toStatus(IncidentStatus.OPEN)
                .detail("Incident created from workflow failure")
                .createdAt(Instant.now())
                .build());
        assertThat(event.getId()).isNotNull();
    }
}
