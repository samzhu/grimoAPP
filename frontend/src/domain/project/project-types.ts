export type WorkflowRecipe = {
  id: string;
  name: string;
  description: string;
  category: string;
};

export type Project = {
  id: string;
  name: string;
  description: string;
  folderPath: string;
  workflowRecipeId: string;
  workflowRecipeName: string;
  status: "ACTIVE" | "NEEDS_CHECK";
  createdAt: string;
  updatedAt: string;
};

export type CreateProjectInput = {
  name: string;
  description: string;
  folderPath: string;
  workflowRecipeId: string;
};
