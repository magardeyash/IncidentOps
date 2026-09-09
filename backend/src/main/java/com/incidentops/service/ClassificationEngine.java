package com.incidentops.service;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.ConfidenceLevel;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ClassificationEngine {

    private static final Pattern TEST_FAILURE_PATTERN = Pattern.compile(
            "(?i)(FAILED|org\\.junit|TestFailedException|AssertionError|FAIL:|FAILURES!|Tests run: [1-9])"
    );
    private static final Pattern DEPENDENCY_ERROR_PATTERN = Pattern.compile(
            "(?i)(could not resolve dependency|404|Could not find artifact|npm ERR! 404|pip install|Could not resolve dependencies|npm ERR! code E404)"
    );
    private static final Pattern BUILD_ERROR_PATTERN = Pattern.compile(
            "(?i)(compilation failed|cannot find symbol|SyntaxError|\\[ERROR\\].*\\.java:|javac|COMPILATION ERROR)"
    );
    private static final Pattern TIMEOUT_PATTERN = Pattern.compile(
            "(?i)(timeout|terminated|timed out|Job exceeded maximum time limit|The command \".*\" timed out)"
    );
    private static final Pattern INFRA_FLAKE_PATTERN = Pattern.compile(
            "(?i)(runner connection lost|infrastructure failure|No space left on device|GitHub Actions runner|No output has been received in)"
    );

    private static final List<String> DEPENDENCY_FILES = List.of(
            "pom.xml", "build.gradle", "package.json", "package-lock.json", "requirements.txt", "go.mod", "Cargo.toml", "yarn.lock"
    );

    @Data
    @Builder
    public static class ClassificationResult {
        private ClassificationType type;
        private ConfidenceLevel confidence;
        private String logExcerpt;
    }

    public ClassificationResult classify(String logContent, List<String> changedFiles) {
        if (logContent == null || logContent.isBlank()) {
            return ClassificationResult.builder()
                    .type(ClassificationType.UNKNOWN)
                    .confidence(ConfidenceLevel.LOW)
                    .logExcerpt("No log output available for classification.")
                    .build();
        }

        String truncatedLog = truncateLog(logContent);

        boolean hasDependencyFileChanged = changedFiles != null && changedFiles.stream()
                .anyMatch(file -> DEPENDENCY_FILES.stream().anyMatch(dep -> file.toLowerCase().endsWith(dep)));

        boolean hasTestFileChanged = changedFiles != null && changedFiles.stream()
                .anyMatch(file -> file.toLowerCase().contains("test") || file.toLowerCase().contains("spec"));

        // Match patterns against log content
        boolean testMatch = TEST_FAILURE_PATTERN.matcher(logContent).find();
        boolean depMatch = DEPENDENCY_ERROR_PATTERN.matcher(logContent).find();
        boolean buildMatch = BUILD_ERROR_PATTERN.matcher(logContent).find();
        boolean timeoutMatch = TIMEOUT_PATTERN.matcher(logContent).find();
        boolean infraMatch = INFRA_FLAKE_PATTERN.matcher(logContent).find();

        if (buildMatch) {
            ConfidenceLevel confidence = hasTestFileChanged ? ConfidenceLevel.MEDIUM : ConfidenceLevel.HIGH;
            return ClassificationResult.builder()
                    .type(ClassificationType.BUILD_ERROR)
                    .confidence(confidence)
                    .logExcerpt(extractMatchingExcerpt(logContent, BUILD_ERROR_PATTERN))
                    .build();
        }

        if (depMatch) {
            ConfidenceLevel confidence = hasDependencyFileChanged ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
            return ClassificationResult.builder()
                    .type(ClassificationType.DEPENDENCY_ERROR)
                    .confidence(confidence)
                    .logExcerpt(extractMatchingExcerpt(logContent, DEPENDENCY_ERROR_PATTERN))
                    .build();
        }

        if (testMatch) {
            ConfidenceLevel confidence = hasTestFileChanged ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
            return ClassificationResult.builder()
                    .type(ClassificationType.TEST_FAILURE)
                    .confidence(confidence)
                    .logExcerpt(extractMatchingExcerpt(logContent, TEST_FAILURE_PATTERN))
                    .build();
        }

        if (timeoutMatch) {
            return ClassificationResult.builder()
                    .type(ClassificationType.TIMEOUT)
                    .confidence(ConfidenceLevel.HIGH)
                    .logExcerpt(extractMatchingExcerpt(logContent, TIMEOUT_PATTERN))
                    .build();
        }

        if (infraMatch) {
            return ClassificationResult.builder()
                    .type(ClassificationType.INFRA_FLAKE)
                    .confidence(ConfidenceLevel.HIGH)
                    .logExcerpt(extractMatchingExcerpt(logContent, INFRA_FLAKE_PATTERN))
                    .build();
        }

        return ClassificationResult.builder()
                .type(ClassificationType.UNKNOWN)
                .confidence(ConfidenceLevel.LOW)
                .logExcerpt(truncatedLog)
                .build();
    }

    private String extractMatchingExcerpt(String logContent, Pattern pattern) {
        String[] lines = logContent.split("\\r?\\n");
        StringBuilder excerpt = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (pattern.matcher(line).find() || count > 0) {
                excerpt.append(line).append("\n");
                count++;
                if (count >= 15) break; // limit excerpt length
            }
        }

        String result = excerpt.toString();
        if (result.isBlank()) {
            result = truncateLog(logContent);
        }
        return truncateLog(result);
    }

    private String truncateLog(String text) {
        if (text == null) return "";
        return text.length() > 2000 ? text.substring(0, 1997) + "..." : text;
    }
}
