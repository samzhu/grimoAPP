import type {
  CollectionResponse,
  CreateProjectInput,
  LocalDirectory,
  Project,
  WorkflowRecipe,
} from "../../domain/project/project-types";

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

export async function listProjects(): Promise<Project[]> {
  const response = await requestJson<CollectionResponse<Project>>("/api/projects");
  return response.content;
}

export async function listWorkflowRecipes(): Promise<WorkflowRecipe[]> {
  const response = await requestJson<CollectionResponse<WorkflowRecipe>>("/api/workflow-recipes");
  return response.content;
}

export function listLocalDirectories(path?: string): Promise<LocalDirectory> {
  const query = path ? `?${new URLSearchParams({ path }).toString()}` : "";
  return requestJson<LocalDirectory>(`/api/local-directories${query}`);
}

export function createProject(input: CreateProjectInput): Promise<Project> {
  return requestJson<Project>("/api/projects", {
    method: "POST",
    body: JSON.stringify(input),
  });
}
