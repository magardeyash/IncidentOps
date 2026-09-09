package com.incidentops;

import com.incidentops.config.JwtUtils;
import com.incidentops.entity.*;
import com.incidentops.enums.*;
import com.incidentops.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentContextIntegrationTest extends AbstractTestcontainersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

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

    private String authToken;
    private Incident testIncident;

    @BeforeEach
    void setUp() {
        failureClassificationRepository.deleteAll();
        incidentEventRepository.deleteAll();
        incidentRepository.deleteAll();
        pipelineRunRepository.deleteAll();
        commitRepository.deleteAll();
        repositoryRepository.deleteAll();

        authToken = "Bearer " + jwtUtils.generateToken("admin@incidentops.com");

        RepositoryEntity repo = repositoryRepository.save(RepositoryEntity.builder()
                .githubRepoId(8801L)
                .fullName("owner/context-repo")
                .webhookSecret("sec")
                .installedAt(Instant.now())
                .active(true)
                .build());

        CommitEntity commit = commitRepository.save(CommitEntity.builder()
                .sha("sha-ctx")
                .repository(repo)
                .authorName("Jane")
                .authorEmail("jane@example.com")
                .message("Update build script")
                .changedFiles(List.of("build.gradle", "src/Test.java"))
                .pushedAt(Instant.now())
                .build());

        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .repository(repo)
                .githubRunId(88010L)
                .branch("feature/test")
                .commitSha(commit.getSha())
                .status(PipelineRunStatus.FAILURE)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build());

        testIncident = incidentRepository.save(Incident.builder()
                .pipelineRun(run)
                .status(IncidentStatus.OPEN)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        FailureClassification classification = failureClassificationRepository.save(FailureClassification.builder()
                .incident(testIncident)
                .type(ClassificationType.TEST_FAILURE)
                .confidence(ConfidenceLevel.HIGH)
                .matchedLogExcerpt("Test failed: AssertionError in UserServiceTest")
                .classifiedAt(Instant.now())
                .build());

        testIncident.setClassification(classification);
        incidentRepository.save(testIncident);

        incidentEventRepository.save(IncidentEvent.builder()
                .incident(testIncident)
                .eventType(EventType.CREATED)
                .toStatus(IncidentStatus.OPEN)
                .detail("Incident created")
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("GET /incidents/{id}/context returns structured context bundle for AI consumption")
    void testGetIncidentContext() throws Exception {
        mockMvc.perform(get("/incidents/" + testIncident.getId() + "/context")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncident.getId().toString()))
                .andExpect(jsonPath("$.repositoryFullName").value("owner/context-repo"))
                .andExpect(jsonPath("$.classificationType").value("TEST_FAILURE"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.recommendedAction").value("RETRY"))
                .andExpect(jsonPath("$.changedFiles[0]").value("build.gradle"))
                .andExpect(jsonPath("$.timelineSummary[0]").value(org.hamcrest.Matchers.containsString("CREATED")));
    }
}
