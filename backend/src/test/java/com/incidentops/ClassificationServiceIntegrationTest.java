package com.incidentops;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.incidentops.entity.*;
import com.incidentops.enums.*;
import com.incidentops.repository.*;
import com.incidentops.service.ClassificationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class ClassificationServiceIntegrationTest extends AbstractTestcontainersTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private ClassificationService classificationService;

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
    private IncidentEventRepository incidentEventRepository;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        failureClassificationRepository.deleteAll();
        incidentEventRepository.deleteAll();
        incidentRepository.deleteAll();
        pipelineRunRepository.deleteAll();
        commitRepository.deleteAll();
        repositoryRepository.deleteAll();
    }

    private Incident createTestIncident(Long githubRunId, String sha) {
        RepositoryEntity repo = repositoryRepository.save(RepositoryEntity.builder()
                .githubRepoId(9999L)
                .fullName("owner/repo")
                .webhookSecret("secret")
                .installedAt(Instant.now())
                .active(true)
                .build());

        CommitEntity commit = commitRepository.save(CommitEntity.builder()
                .sha(sha)
                .repository(repo)
                .authorName("Dev")
                .authorEmail("dev@example.com")
                .message("Add feature")
                .changedFiles(List.of("src/test/java/AppTest.java"))
                .pushedAt(Instant.now())
                .build());

        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .repository(repo)
                .githubRunId(githubRunId)
                .branch("main")
                .commitSha(sha)
                .status(PipelineRunStatus.FAILURE)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build());

        return incidentRepository.save(Incident.builder()
                .pipelineRun(run)
                .status(IncidentStatus.OPEN)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Async classification fetches logs from WireMock and sets FailureClassification")
    void testSuccessfulClassification() {
        Long runId = 8881L;
        String sha = "sha8881";
        Incident incident = createTestIncident(runId, sha);

        wireMockServer.stubFor(get(urlEqualTo("/repos/owner/repo/actions/runs/8881/logs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("[ERROR] Tests run: 5, Failures: 1, Errors: 0 - AppTest failed")));

        classificationService.classifyFailureAsync(incident.getId());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<FailureClassification> classOpt = failureClassificationRepository.findByIncidentId(incident.getId());
            assertThat(classOpt).isPresent();
            assertThat(classOpt.get().getType()).isEqualTo(ClassificationType.TEST_FAILURE);
            assertThat(classOpt.get().getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
        });

        List<IncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        assertThat(events).anyMatch(e -> e.getEventType() == EventType.CLASSIFIED);
    }

    @Test
    @DisplayName("Resilience: GitHub API 500 error triggers fallback to UNKNOWN without breaking Incident state")
    void testGitHubApiFailureFallback() {
        Long runId = 8882L;
        String sha = "sha8882";
        Incident incident = createTestIncident(runId, sha);

        wireMockServer.stubFor(get(urlEqualTo("/repos/owner/repo/actions/runs/8882/logs"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        classificationService.classifyFailureAsync(incident.getId());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<FailureClassification> classOpt = failureClassificationRepository.findByIncidentId(incident.getId());
            assertThat(classOpt).isPresent();
            assertThat(classOpt.get().getType()).isEqualTo(ClassificationType.UNKNOWN);
            assertThat(classOpt.get().getConfidence()).isEqualTo(ConfidenceLevel.LOW);
        });

        Optional<Incident> reloadedIncident = incidentRepository.findById(incident.getId());
        assertThat(reloadedIncident).isPresent();
        assertThat(reloadedIncident.get().getStatus()).isEqualTo(IncidentStatus.OPEN);
    }
}
