import type { Task, TaskState, WorkflowSummary } from "../../domain/task/task-types";

export type CreateTaskInput = {
  title: string;
  body: string;
  labels: string[];
};

type CollectionResponse<T> = {
  content: T[];
};

type TaskResponse = {
  id: string;
  projectId: string;
  title: string;
  body: string;
  description: string;
  state: TaskState;
  source: string;
  workflowRecipeId: string;
  workflowSummary: WorkflowSummary;
  updatedAt: string;
  acceptance: string[];
  gaps: string[];
  evidence: string[];
  labels: string[];
  commentCount: number;
};

type ApiErrorBody = {
  error?: string;
};

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiErrorBody;
    throw new Error(body.error || "操作失敗，請稍後再試");
  }
  return (await response.json()) as T;
}

export async function listTasks(projectId: string): Promise<Task[]> {
  const response = await requestJson<CollectionResponse<TaskResponse>>(
    `/api/projects/${projectId}/tasks`,
  );
  return response.content.map(toTask);
}

export async function createTask(projectId: string, input: CreateTaskInput): Promise<Task> {
  const task = await requestJson<TaskResponse>(`/api/projects/${projectId}/tasks`, {
    method: "POST",
    body: JSON.stringify(input),
  });
  return toTask(task);
}

function toTask(response: TaskResponse): Task {
  const qualityScore = response.workflowSummary.qualityScore ?? 0;
  return {
    id: response.id,
    projectId: response.projectId,
    title: response.title,
    state: response.state,
    source: response.source,
    skill: "workflow-recipe",
    score: qualityScore,
    step: response.workflowSummary.currentStep ?? "Backlog",
    workflowRecipeId: response.workflowRecipeId,
    workflowSummary: response.workflowSummary,
    updatedAt: formatUpdatedAt(response.updatedAt),
    body: response.body,
    description: response.description || response.body,
    acceptance: response.acceptance,
    gaps: response.gaps,
    evidence: response.evidence,
    labels: response.labels,
    commentCount: response.commentCount,
  };
}

function formatUpdatedAt(value: string) {
  return new Date(value).toLocaleString("zh-TW", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
