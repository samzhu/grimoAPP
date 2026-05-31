import type { CreateProjectInput, Project, WorkflowRecipe } from "../../domain/project/project-types";

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

export function listProjects(): Promise<Project[]> {
  return requestJson<Project[]>("/api/projects");
}

export function listWorkflowRecipes(): Promise<WorkflowRecipe[]> {
  return requestJson<WorkflowRecipe[]>("/api/workflow-recipes");
}

export function createProject(input: CreateProjectInput): Promise<Project> {
  return requestJson<Project>("/api/projects", {
    method: "POST",
    body: JSON.stringify(input),
  });
}
