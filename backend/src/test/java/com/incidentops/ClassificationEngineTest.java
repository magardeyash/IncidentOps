package com.incidentops;

import com.incidentops.enums.ClassificationType;
import com.incidentops.enums.ConfidenceLevel;
import com.incidentops.service.ClassificationEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationEngineTest {

    private final ClassificationEngine engine = new ClassificationEngine();

    @Test
    @DisplayName("Classify TEST_FAILURE with JUnit log sample and changed test file")
    void testTestFailureClassification() {
        String log = """
                [INFO] Running com.example.UserServiceTest
                [ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.12 s <<< FAILURE!
                [ERROR] testUserCreation -- AssertionError: Expected user ID to be non-null
                [INFO] BUILD FAILURE
                """;
        List<String> changedFiles = List.of("src/test/java/UserServiceTest.java");

        ClassificationEngine.ClassificationResult result = engine.classify(log, changedFiles);

        assertThat(result.getType()).isEqualTo(ClassificationType.TEST_FAILURE);
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.getLogExcerpt()).contains("AssertionError");
    }

    @Test
    @DisplayName("Classify DEPENDENCY_ERROR with npm 404 log sample and package.json change")
    void testDependencyErrorClassification() {
        String log = """
                npm ERR! code E404
                npm ERR! 404 Not Found - GET https://registry.npmjs.org/@invalid/pkg - Not found
                npm ERR! 404 could not resolve dependency '@invalid/pkg@1.0.0'
                """;
        List<String> changedFiles = List.of("package.json");

        ClassificationEngine.ClassificationResult result = engine.classify(log, changedFiles);

        assertThat(result.getType()).isEqualTo(ClassificationType.DEPENDENCY_ERROR);
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    @DisplayName("Classify BUILD_ERROR with javac compiler error sample")
    void testBuildErrorClassification() {
        String log = """
                [ERROR] /app/src/main/java/Main.java:[15,24] cannot find symbol
                [ERROR]   symbol:   class NonExistentClass
                [ERROR] COMPILATION ERROR
                """;
        List<String> changedFiles = List.of("src/main/java/Main.java");

        ClassificationEngine.ClassificationResult result = engine.classify(log, changedFiles);

        assertThat(result.getType()).isEqualTo(ClassificationType.BUILD_ERROR);
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    @DisplayName("Classify TIMEOUT when job execution time limit is exceeded")
    void testTimeoutClassification() {
        String log = """
                The job exceeded maximum time limit of 60 minutes.
                Process terminated by GitHub Actions runner.
                """;

        ClassificationEngine.ClassificationResult result = engine.classify(log, List.of());

        assertThat(result.getType()).isEqualTo(ClassificationType.TIMEOUT);
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    @DisplayName("Classify INFRA_FLAKE when runner connection is lost")
    void testInfraFlakeClassification() {
        String log = """
                runner connection lost. Unable to contact GitHub Actions runner instance.
                """;

        ClassificationEngine.ClassificationResult result = engine.classify(log, List.of());

        assertThat(result.getType()).isEqualTo(ClassificationType.INFRA_FLAKE);
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    @DisplayName("Fallback to UNKNOWN classification when log does not match known rules")
    void testUnknownFallback() {
        String log = """
                An unrecognized error occurred in step 5.
                Exited with status 1.
                """;

        ClassificationEngine.ClassificationResult result = engine.classify(log, List.of());

        assertThat(result.getType()).isEqualTo(ClassificationType.UNKNOWN);
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.LOW);
    }
}
