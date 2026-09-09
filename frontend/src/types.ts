export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-based)
  size: number;
}

export interface Incident {
  id: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'IGNORED';
  version: number;
  createdAt: string;
  updatedAt: string;
  assignedNotes: string | null;
  repository: Repository;
  pipelineRun: PipelineRun;
  classification: Classification | null;
  playbook: Playbook | null;
  events: IncidentEvent[];
}

export interface Repository {
  id: string;
  githubRepoId: number;
  fullName: string;
  installedAt: string;
  active: boolean;
}

export interface PipelineRun {
  id: string;
  githubRunId: number;
  branch: string;
  commitSha: string;
  status: string;
  startedAt: string;
  finishedAt: string;
}

export interface Classification {
  id: string;
  type: string;
  matchedLogExcerpt: string | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  classifiedAt: string;
}

export interface Playbook {
  id: string;
  classificationType: string;
  recommendedAction: string;
  description: string;
}

export interface IncidentEvent {
  id: string;
  eventType: string;
  fromStatus: string | null;
  toStatus: string | null;
  detail: string | null;
  createdAt: string;
}

export interface IncidentContext {
  incidentId: string;
  status: string;
  repositoryFullName: string;
  branch: string;
  commitSha: string;
  classificationType: string | null;
  confidence: string | null;
  matchedLogExcerpt: string | null;
  recommendedAction: string | null;
  playbookDescription: string | null;
  changedFiles: string[];
  timelineSummary: string[];
  assignedNotes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Postmortem {
  id: string;
  incidentId: string;
  summary: string | null;
  timeline: string | null;
  rootCause: string | null;
  recommendedFollowUp: string | null;
  generatedAt: string;
  editedAt: string | null;
}

export interface ApiError {
  status: number;
  message: string;
}
