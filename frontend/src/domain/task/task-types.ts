export type TaskState =
  | "BACKLOG"
  | "DEFINING"
  | "READY"
  | "RUNNING"
  | "REVIEW"
  | "DONE"
  | "BLOCKED";

export type Task = {
  id: string;
  title: string;
  state: TaskState;
  source: string;
  skill: string;
  score: number;
  step: string;
  updatedAt: string;
  description: string;
  acceptance: string[];
  gaps: string[];
  evidence: string[];
  labels: string[];
  comments: number;
};

