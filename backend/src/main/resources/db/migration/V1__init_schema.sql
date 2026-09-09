-- V1 Schema Initialization for IncidentOps

CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    github_repo_id BIGINT NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    webhook_secret VARCHAR(255) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id),
    github_run_id BIGINT NOT NULL UNIQUE,
    branch VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE commits (
    id UUID PRIMARY KEY,
    sha VARCHAR(255) NOT NULL,
    repository_id UUID NOT NULL REFERENCES repositories(id),
    author_name VARCHAR(255),
    author_email VARCHAR(255),
    message TEXT,
    pushed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE commit_files (
    commit_id UUID NOT NULL REFERENCES commits(id) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL
);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL UNIQUE REFERENCES pipeline_runs(id),
    status VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    assigned_notes TEXT
);

CREATE TABLE failure_classifications (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL UNIQUE REFERENCES incidents(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    matched_log_excerpt TEXT,
    confidence VARCHAR(50) NOT NULL,
    classified_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE playbooks (
    id UUID PRIMARY KEY,
    classification_type VARCHAR(50) NOT NULL UNIQUE,
    recommended_action VARCHAR(50) NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE incident_events (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE postmortems (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL UNIQUE REFERENCES incidents(id) ON DELETE CASCADE,
    summary TEXT,
    timeline TEXT,
    root_cause TEXT,
    recommended_follow_up TEXT,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    edited_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE processed_deliveries (
    delivery_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for frequent queries
CREATE INDEX idx_pipeline_runs_github_run_id ON pipeline_runs(github_run_id);
CREATE INDEX idx_commits_sha ON commits(sha);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incident_events_incident_id ON incident_events(incident_id);

-- Seed Playbook entries for each ClassificationType
INSERT INTO playbooks (id, classification_type, recommended_action, description) VALUES
('11111111-1111-1111-1111-111111111111', 'BUILD_ERROR', 'MANUAL_FIX', 'Compilation or syntax error. Review code changes and build output.'),
('22222222-2222-2222-2222-222222222222', 'TEST_FAILURE', 'RETRY', 'Automated unit/integration test failure. Inspect failed test suite and re-run workflow.'),
('33333333-3333-3333-3333-333333333333', 'DEPENDENCY_ERROR', 'MANUAL_FIX', 'Package resolution or lockfile mismatch. Verify dependency repository availability and versions.'),
('44444444-4444-4444-4444-444444444444', 'TIMEOUT', 'RETRY', 'Workflow step exceeded maximum allowed execution time. Retry pipeline or optimize step duration.'),
('55555555-5555-5555-5555-555555555555', 'INFRA_FLAKE', 'RETRY', 'Transient runner or network failure. Re-trigger workflow run.'),
('66666666-6666-6666-6666-666666666666', 'UNKNOWN', 'INVESTIGATE_INFRA', 'Unclassified failure pattern. Manually inspect job logs and system state.');
