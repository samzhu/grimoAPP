import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { Project, WorkflowRecipe } from "../../domain/project/project-types";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";
import { chooseNativeProjectPath, createProject, listProjects, listWorkflowRecipes } from "./project-api";

type ProjectsProps = {
  initialViewMode?: "list" | "create";
  projects?: Project[];
  onProjectsChange?: (projects: Project[]) => void;
  onCurrentProjectChange: (project: Project) => void;
};

const emptyForm = {
  name: "",
  description: "",
  projectPath: "",
  workflowRecipeId: "",
};

const defaultWorkflowRecipeId = "web-service-development";

export function Projects({
  initialViewMode = "list",
  projects: appProjects,
  onProjectsChange,
  onCurrentProjectChange,
}: ProjectsProps) {
  const [projects, setProjects] = useState<Project[]>(appProjects ?? []);
  const [recipes, setRecipes] = useState<WorkflowRecipe[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [viewMode, setViewMode] = useState<"list" | "create">(initialViewMode);
  const [isLoading, setIsLoading] = useState(!appProjects);
  const [isWorkflowLoading, setIsWorkflowLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isNativeDialogLoading, setIsNativeDialogLoading] = useState(false);
  const [nativeDialogError, setNativeDialogError] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    setViewMode(initialViewMode);
  }, [initialViewMode]);

  useEffect(() => {
    if (appProjects) {
      setProjects(appProjects);
      setIsLoading(false);
    }
  }, [appProjects]);

  useEffect(() => {
    if (appProjects) {
      return;
    }
    let isMounted = true;
    listProjects()
      .then((projectList) => {
        if (!isMounted) {
          return;
        }
        setProjects(projectList);
        onProjectsChange?.(projectList);
      })
      .catch((caught: unknown) => {
        if (isMounted) {
          setError(caught instanceof Error ? caught.message : "專案資料載入失敗");
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });
    return () => {
      isMounted = false;
    };
  }, [appProjects, onProjectsChange]);

  const selectedRecipe = recipes.find((recipe) => recipe.id === form.workflowRecipeId);
  const canSubmit =
    form.name.trim().length > 0 &&
    form.workflowRecipeId.trim().length > 0;

  const preferredWorkflowRecipeId = (recipeList: WorkflowRecipe[]) =>
    recipeList.find((recipe) => recipe.id === defaultWorkflowRecipeId)?.id || recipeList[0]?.id || "";

  const loadWorkflowRecipesForForm = async () => {
    if (recipes.length > 0) {
      setForm((current) => ({
        ...current,
        workflowRecipeId: current.workflowRecipeId || preferredWorkflowRecipeId(recipes),
      }));
      return;
    }
    setIsWorkflowLoading(true);
    try {
      const recipeList = await listWorkflowRecipes();
      setRecipes(recipeList);
      setForm((current) => ({
        ...current,
        workflowRecipeId: current.workflowRecipeId || preferredWorkflowRecipeId(recipeList),
      }));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "工作流清單載入失敗");
    } finally {
      setIsWorkflowLoading(false);
    }
  };

  const startProjectCreation = () => {
    setError("");
    setMessage("");
    setNativeDialogError("");
    setViewMode("create");
  };

  useEffect(() => {
    if (viewMode === "create") {
      void loadWorkflowRecipesForForm();
    }
  }, [viewMode]);

  const submitProject = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    setError("");
    setMessage("");
    setIsSubmitting(true);
    try {
      const projectPath = form.projectPath.trim();
      const project = await createProject({
        name: form.name.trim(),
        description: form.description.trim(),
        ...(projectPath ? { projectPath } : {}),
        workflowRecipeId: form.workflowRecipeId,
      });
      const nextProjects = [project, ...projects];
      setProjects(nextProjects);
      onProjectsChange?.(nextProjects);
      onCurrentProjectChange(project);
      setForm(emptyForm);
      setViewMode("list");
      setMessage(`${project.name} 已建立並設為目前專案`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "建立專案失敗");
    } finally {
      setIsSubmitting(false);
    }
  };

  const openNativeFolderDialog = async () => {
    if (isNativeDialogLoading) {
      return;
    }
    setNativeDialogError("");
    setIsNativeDialogLoading(true);
    try {
      const currentProjectPath = form.projectPath.trim();
      const result = await chooseNativeProjectPath({
        ...(currentProjectPath ? { initialPath: currentProjectPath } : {}),
        title: "選擇 Project 資料夾",
      });
      if (result.selected) {
        setForm((current) => ({ ...current, projectPath: result.projectPath }));
      }
    } catch (caught) {
      setNativeDialogError(
        caught instanceof Error ? caught.message : "無法開啟系統資料夾選擇器，請手動貼上路徑",
      );
    } finally {
      setIsNativeDialogLoading(false);
    }
  };

  return (
    <section className="projects-view">
      <div className="section-head">
        <div>
          <h1>{viewMode === "create" ? "新增專案" : "專案管理"}</h1>
          <p>
            {viewMode === "create"
              ? "建立 Project 的基本資料、專案路徑與工作流。"
              : "管理本機 repo / codebase，讓任務、執行紀錄與審查資料有明確歸屬。"}
          </p>
        </div>
        {viewMode === "create" ? (
          <button className="icon-text-button" type="button" onClick={() => setViewMode("list")}>
            返回列表
          </button>
        ) : (
          <button className="primary-button" type="button" onClick={startProjectCreation}>
            新增專案
          </button>
        )}
      </div>
      <div className="project-grid single">
        {viewMode === "list" ? (
        <Panel title="Project list">
          {isLoading ? (
            <p className="form-note">載入專案中...</p>
          ) : projects.length === 0 ? (
            <p className="form-note">尚未建立專案</p>
          ) : (
            <div className="project-list-stack">
              {projects.map((project) => (
                <button
                  className="project-list-card"
                  key={project.id}
                  type="button"
                  onClick={() => onCurrentProjectChange(project)}
                >
                  <strong>{project.name}</strong>
                  <Metric label="專案路徑" value={project.projectPath} />
                  <Metric label="工作流" value={project.workflowRecipeName} />
                  <Metric label="狀態" value={project.status} />
                </button>
              ))}
            </div>
          )}
        </Panel>
        ) : (
        <Panel title="新增專案">
          <form className="form-stack" onSubmit={submitProject}>
            <label>
              專案名稱
              <input
                name="name"
                placeholder="例如 grimoAPP"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </label>
            <label>
              專案描述
              <textarea
                name="description"
                placeholder="這個專案的產品目標、主要工作類型或目前焦點"
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </label>
            <div className="form-field-stack">
              <label htmlFor="projectPath">專案路徑</label>
              <div className="folder-picker-field">
                <input
                  id="projectPath"
                  name="projectPath"
                  placeholder="/Users/samzhu/workspace/github-samzhu/grimoAPP"
                  value={form.projectPath}
                  onChange={(event) => setForm({ ...form, projectPath: event.target.value })}
                />
                <button
                  className="icon-text-button"
                  type="button"
                  disabled={isNativeDialogLoading}
                  onClick={() => void openNativeFolderDialog()}
                >
                  {isNativeDialogLoading ? "開啟中..." : "選擇資料夾"}
                </button>
              </div>
            </div>
            <p className="form-note">未填會使用 Grimo 預設路徑</p>
            {isNativeDialogLoading && <p className="form-note">正在開啟系統資料夾選擇器...</p>}
            {nativeDialogError && <p className="form-message error">{nativeDialogError}</p>}
            <label>
              專案工作流
              <select
                name="workflowRecipeId"
                value={form.workflowRecipeId}
                onChange={(event) => setForm({ ...form, workflowRecipeId: event.target.value })}
              >
                {isWorkflowLoading ? (
                  <option value="">載入工作流中...</option>
                ) : (
                  recipes.map((recipe) => (
                    <option key={recipe.id} value={recipe.id}>
                      {recipe.name}
                    </option>
                  ))
                )}
              </select>
            </label>
            {selectedRecipe && (
              <div className="workflow-preview">
                <div>
                  <strong>{selectedRecipe.name}</strong>
                  <p>{selectedRecipe.description}</p>
                </div>
                {selectedRecipe.steps.length > 0 && (
                  <ol className="workflow-step-list">
                    {selectedRecipe.steps.map((step) => (
                      <li key={step.id}>
                        <span>{step.name}</span>
                        <p>{step.taskState}</p>
                      </li>
                    ))}
                  </ol>
                )}
                {selectedRecipe.qualityLoopSummary && (
                  <p className="form-note">{selectedRecipe.qualityLoopSummary}</p>
                )}
              </div>
            )}
            <div className="role-preview">
              <strong>參與角色</strong>
              {selectedRecipe && selectedRecipe.roles.length > 0 ? (
                <div className="role-list">
                  {selectedRecipe.roles.map((role) => (
                    <div className="role-row" key={role.id}>
                      <span>{role.name}</span>
                      <p>{role.description}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="form-note">這個工作流尚未定義角色</p>
              )}
            </div>
            {error && <p className="form-message error">{error}</p>}
            {message && <p className="form-message success">{message}</p>}
            <button type="submit" className="primary-button" disabled={!canSubmit || isSubmitting}>
              {isSubmitting ? "建立中..." : "建立專案"}
            </button>
          </form>
        </Panel>
        )}
      </div>
      {viewMode === "list" && message && <p className="form-message success">{message}</p>}
      {viewMode === "list" && error && <p className="form-message error">{error}</p>}
    </section>
  );
}
