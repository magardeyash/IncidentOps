package com.incidentops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentops.config.JwtUtils;
import com.incidentops.dto.PostmortemUpdateRequest;
import com.incidentops.entity.*;
import com.incidentops.enums.EventType;
import com.incidentops.enums.IncidentStatus;
import com.incidentops.enums.PipelineRunStatus;
import com.incidentops.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostmortemIntegrationTest extends AbstractTestcontainersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PipelineRunRepository pipelineRunRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Autowired
    private PostmortemRepository postmortemRepository;

    private String authToken;
    private Incident testIncident;

    @BeforeEach
    void setUp() {
        postmortemRepository.deleteAll();
        incidentEventRepository.deleteAll();
        incidentRepository.deleteAll();
        pipelineRunRepository.deleteAll();
        repositoryRepository.deleteAll();

        authToken = "Bearer " + jwtUtils.generateToken("admin@incidentops.com");

        RepositoryEntity repo = repositoryRepository.save(RepositoryEntity.builder()
                .githubRepoId(9901L)
                .fullName("owner/postmortem-repo")
                .webhookSecret("sec")
                .installedAt(Instant.now())
                .active(true)
                .build());

        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .repository(repo)
                .githubRunId(99010L)
                .branch("main")
                .commitSha("sha-pm")
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

        incidentEventRepository.save(IncidentEvent.builder()
                .incident(testIncident)
                .eventType(EventType.CREATED)
                .toStatus(IncidentStatus.OPEN)
                .detail("Incident created")
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Generate postmortem on OPEN incident returns 409 Conflict")
    void testPostmortemGenerationFailsWhenOpen() throws Exception {
        mockMvc.perform(post("/incidents/" + testIncident.getId() + "/postmortem")
                        .header("Authorization", authToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Postmortem generation is only allowed when incident status is RESOLVED or IGNORED. Current status: OPEN"));
    }

    @Test
    @DisplayName("Generate, retrieve, and update postmortem on RESOLVED incident succeeds")
    void testFullPostmortemLifecycle() throws Exception {
        // Update incident to RESOLVED
        testIncident.setStatus(IncidentStatus.RESOLVED);
        incidentRepository.save(testIncident);

        incidentEventRepository.save(IncidentEvent.builder()
                .incident(testIncident)
                .eventType(EventType.STATE_CHANGED)
                .fromStatus(IncidentStatus.INVESTIGATING)
                .toStatus(IncidentStatus.RESOLVED)
                .detail("Resolved manually by team")
                .createdAt(Instant.now())
                .build());

        // POST generate postmortem
        mockMvc.perform(post("/incidents/" + testIncident.getId() + "/postmortem")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(testIncident.getId().toString()))
                .andExpect(jsonPath("$.timeline").value(org.hamcrest.Matchers.containsString("Incident created")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Postmortem Report")));

        // GET postmortem
        mockMvc.perform(get("/incidents/" + testIncident.getId() + "/postmortem")
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Postmortem Report")));

        // PATCH update postmortem
        PostmortemUpdateRequest updateReq = PostmortemUpdateRequest.builder()
                .summary("Updated Custom Summary for Postmortem")
                .recommendedFollowUp("Add robust retry policy to CI workflow")
                .build();

        mockMvc.perform(patch("/incidents/" + testIncident.getId() + "/postmortem")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Updated Custom Summary for Postmortem"))
                .andExpect(jsonPath("$.recommendedFollowUp").value("Add robust retry policy to CI workflow"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
    }
}
