package com.incidentops.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

@Service
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final WebClient webClient;

    public GitHubApiClient(WebClient.Builder webClientBuilder,
                           @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
                           @Value("${github.api.token:}") String token) {
        WebClient.Builder builder = webClientBuilder.baseUrl(baseUrl);
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        this.webClient = builder
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fetchCommitFallback")
    @RateLimiter(name = "githubApi")
    public GitHubCommitResponse fetchCommitDetails(String owner, String repo, String sha) {
        log.info("Fetching commit details from GitHub API for {}/{} sha {}", owner, repo, sha);
        return webClient.get()
                .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                .retrieve()
                .bodyToMono(GitHubCommitResponse.class)
                .block();
    }

    public GitHubCommitResponse fetchCommitFallback(String owner, String repo, String sha, Throwable t) {
        log.warn("Fallback triggered for fetchCommitDetails ({}/{} {}): {}", owner, repo, sha, t.getMessage());
        GitHubCommitResponse fallback = new GitHubCommitResponse();
        fallback.setSha(sha);
        GitHubCommitDetail detail = new GitHubCommitDetail();
        detail.setMessage("Commit details unavailable due to external API failure");
        fallback.setCommit(detail);
        fallback.setFiles(Collections.emptyList());
        return fallback;
    }

    @CircuitBreaker(name = "githubApi", fallbackMethod = "fetchLogsFallback")
    @RateLimiter(name = "githubApi")
    public String fetchWorkflowRunLogs(String owner, String repo, Long runId) {
        log.info("Fetching workflow run logs from GitHub API for {}/{} runId {}", owner, repo, runId);
        return webClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs/{run_id}/logs", owner, repo, runId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String fetchLogsFallback(String owner, String repo, Long runId, Throwable t) {
        log.warn("Fallback triggered for fetchWorkflowRunLogs ({}/{} run {}): {}", owner, repo, runId, t.getMessage());
        return null;
    }

    @Data
    public static class GitHubCommitResponse {
        private String sha;
        private GitHubCommitDetail commit;
        private List<GitHubCommitFile> files;
    }

    @Data
    public static class GitHubCommitDetail {
        private String message;
        private GitHubUser author;
        private GitHubUser committer;
    }

    @Data
    public static class GitHubUser {
        private String name;
        private String email;
    }

    @Data
    public static class GitHubCommitFile {
        private String filename;
        private String status;
    }
}
