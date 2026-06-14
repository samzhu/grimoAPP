export type TaskState =
  | "BACKLOG"
  | "DEFINING"
  | "READY"
  | "RUNNING"
  | "REVIEW"
  | "DONE";

export type WorkflowSummary = {
  currentStep: string | null;
  qualityScore: number | null;
};

export type Task = {
  id: string;
  projectId?: string;
  title: string;
  state: TaskState;
  source: string;
  skill: string;
  score: number;
  step: string;
  workflowRecipeId?: string;
  workflowSummary: WorkflowSummary;
  updatedAt: string;
  body?: string;
  description: string;
  acceptance: string[];
  gaps: string[];
  evidence: string[];
  labels: string[];
  commentCount: number;
};
