export type WorkflowRecipe = {
  id: string;
  name: string;
  description: string;
  category: string;
  steps: WorkflowStep[];
  roles: WorkflowRole[];
  qualityLoopSummary: string;
};

export type WorkflowStep = {
  id: string;
  name: string;
  taskState: "BACKLOG" | "DEFINING" | "READY" | "RUNNING" | "REVIEW" | "DONE" | "BLOCKED";
};

export type WorkflowRole = {
  id: string;
  name: string;
  description: string;
  primarySteps: string[];
};

export type Project = {
  id: string;
  name: string;
  description: string;
  projectPath: string;
  workflowRecipeId: string;
  workflowRecipeName: string;
  status: "ACTIVE" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
  workflowRoles: ProjectWorkflowRole[];
};

export type ProjectWorkflowRole = {
  id: string;
  name: string;
  description: string;
  primarySteps: string[];
  enabled: boolean;
};

export type CreateProjectInput = {
  name: string;
  description: string;
  projectPath?: string;
  workflowRecipeId: string;
};

export type LocalDirectoryEntry = {
  name: string;
  path: string;
};

export type LocalDirectoryListing = {
  path: string;
  parentPath: string | null;
  directories: LocalDirectoryEntry[];
};

export type LocalDirectoryQuery =
  | { path?: string; location?: never }
  | { location?: "home" | "default"; path?: never };

export type LocalDirectoryCreateRequest = {
  parentPath: string;
  name: string;
};

export type CollectionResponse<T> = {
  content: T[];
};
