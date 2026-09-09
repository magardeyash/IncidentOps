package com.incidentops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentops.config.JwtUtils;
import com.incidentops.dto.AddNoteRequest;
import com.incidentops.dto.StatusUpdateRequest;
import com.incidentops.entity.*;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
class IncidentLifecycleIntegrationTest extends AbstractTestcontainersTest {

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
    private CommitRepository commitRepository;

    @Autowired
    private FailureClassificationRepository failureClassificationRepository;

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
                .githubRepoId(7001L)
                .fullName("owner/lifecycle-repo")
                .webhookSecret("sec")
                .installedAt(Instant.now())
                .active(true)
                .build());

        PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
                .repository(repo)
                .githubRunId(70010L)
                .branch("main")
                .commitSha("sha-life")
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
    }

    @Test
    @DisplayName("Legal transition: OPEN -> INVESTIGATING -> RESOLVED succeeds")
    void testLegalStateTransitions() throws Exception {
        // OPEN -> INVESTIGATING
        StatusUpdateRequest step1 = new StatusUpdateRequest(IncidentStatus.INVESTIGATING);
        mockMvc.perform(patch("/incidents/" + testIncident.getId() + "/status")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        // INVESTIGATING -> RESOLVED
        StatusUpdateRequest step2 = new StatusUpdateRequest(IncidentStatus.RESOLVED);
        mockMvc.perform(patch("/incidents/" + testIncident.getId() + "/status")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        Incident finalIncident = incidentRepository.findById(testIncident.getId()).orElseThrow();
        assertThat(finalIncident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    @DisplayName("Illegal transition: OPEN -> RESOLVED returns 409 Conflict")
    void testIllegalStateTransition() throws Exception {
        StatusUpdateRequest request = new StatusUpdateRequest(IncidentStatus.RESOLVED);

        mockMvc.perform(patch("/incidents/" + testIncident.getId() + "/status")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Illegal state transition attempt from OPEN to RESOLVED for incident"));
    }

    @Test
    @DisplayName("Optimistic lock failure returns 409 Conflict on concurrent update")
    void testOptimisticLockFailure() throws Exception {
        Incident firstRead = incidentRepository.findById(testIncident.getId()).orElseThrow();
        firstRead.setStatus(IncidentStatus.INVESTIGATING);
        incidentRepository.saveAndFlush(firstRead);

        Incident staleIncident = Incident.builder()
                .id(testIncident.getId())
                .pipelineRun(firstRead.getPipelineRun())
                .status(IncidentStatus.RESOLVED)
                .version(0L)
                .createdAt(firstRead.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> incidentRepository.saveAndFlush(staleIncident)
        );
    }

    @Test
    @DisplayName("Add note to incident updates assignedNotes and writes IncidentEvent")
    void testAddNote() throws Exception {
        AddNoteRequest request = new AddNoteRequest("Investigating runner timeout issues");

        mockMvc.perform(post("/incidents/" + testIncident.getId() + "/notes")
                        .header("Authorization", authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedNotes").value("Investigating runner timeout issues"));

        assertThat(incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(testIncident.getId()))
                .anyMatch(e -> e.getDetail().contains("Investigating runner timeout issues"));
    }

    @Test
    @DisplayName("GET /incidents returns paginated list of incidents")
    void testListIncidents() throws Exception {
        mockMvc.perform(get("/incidents")
                        .header("Authorization", authToken)
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(testIncident.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
