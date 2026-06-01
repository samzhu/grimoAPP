import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { LocalDirectory, Project, WorkflowRecipe } from "../../domain/project/project-types";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";
import { createProject, listLocalDirectories, listProjects, listWorkflowRecipes } from "./project-api";

type ProjectsProps = {
  onCurrentProjectChange: (project: Project) => void;
};

const emptyForm = {
  name: "",
  description: "",
  workspacePath: "",
  workflowRecipeId: "",
};

const defaultWorkflowRecipeId = "web-service-development";

export function Projects({ onCurrentProjectChange }: ProjectsProps) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [recipes, setRecipes] = useState<WorkflowRecipe[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [isWorkflowLoading, setIsWorkflowLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [localDirectory, setLocalDirectory] = useState<LocalDirectory | null>(null);
  const [isDirectoryLoading, setIsDirectoryLoading] = useState(false);
  const [directoryError, setDirectoryError] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let isMounted = true;
    listProjects()
      .then((projectList) => {
        if (!isMounted) {
          return;
        }
        setProjects(projectList);
        if (projectList[0]) {
          onCurrentProjectChange(projectList[0]);
        }
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
  }, [onCurrentProjectChange]);

  const selectedRecipe = recipes.find((recipe) => recipe.id === form.workflowRecipeId);
  const canSubmit =
    form.name.trim().length > 0 &&
    form.workspacePath.trim().length > 0 &&
    form.workflowRecipeId.trim().length > 0;

  const preferredWorkflowRecipeId = (recipeList: WorkflowRecipe[]) =>
    recipeList.find((recipe) => recipe.id === defaultWorkflowRecipeId)?.id || recipeList[0]?.id || "";

  const startProjectCreation = async () => {
    setError("");
    setMessage("");
    setIsCreating(true);
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

  const openFolderPicker = async (path?: string) => {
    setDirectoryError("");
    setIsDirectoryLoading(true);
    try {
      const directory = await listLocalDirectories(path || form.workspacePath.trim() || undefined);
      setLocalDirectory(directory);
    } catch (caught) {
      setDirectoryError(caught instanceof Error ? caught.message : "資料夾清單載入失敗");
    } finally {
      setIsDirectoryLoading(false);
    }
  };

  const submitProject = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    setError("");
    setMessage("");
    setIsSubmitting(true);
    try {
      const project = await createProject({
        name: form.name.trim(),
        description: form.description.trim(),
        workspacePath: form.workspacePath.trim(),
        workflowRecipeId: form.workflowRecipeId,
      });
      setProjects((current) => [project, ...current]);
      onCurrentProjectChange(project);
      setForm(emptyForm);
      setLocalDirectory(null);
      setIsCreating(false);
      setMessage(`${project.name} 已建立並設為目前專案`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "建立專案失敗");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="projects-view">
      <div className="section-head">
        <div>
          <h1>專案管理</h1>
          <p>管理本機 repo / codebase，讓任務、執行紀錄與審查資料有明確歸屬。</p>
        </div>
        {isCreating ? (
          <button className="icon-text-button" type="button" onClick={() => setIsCreating(false)}>
            回到列表
          </button>
        ) : (
          <button className="primary-button" type="button" onClick={startProjectCreation}>
            建立專案
          </button>
        )}
      </div>
      <div className="project-grid">
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
                  <Metric label="工作區" value={project.workspacePath} />
                  <Metric label="工作流" value={project.workflowRecipeName} />
                  <Metric label="狀態" value={project.status} />
                </button>
              ))}
            </div>
          )}
        </Panel>
        {isCreating ? (
        <Panel title="建立專案">
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
            <div className="folder-picker-field">
              <label>
                專案工作區
                <input
                  name="workspacePath"
                  placeholder="/Users/samzhu/workspace/github-samzhu/grimoAPP"
                  value={form.workspacePath}
                  onChange={(event) => setForm({ ...form, workspacePath: event.target.value })}
                />
              </label>
              <button
                className="icon-text-button"
                type="button"
                disabled={isDirectoryLoading}
                onClick={() => openFolderPicker()}
              >
                {isDirectoryLoading ? "讀取中..." : "選擇資料夾"}
              </button>
            </div>
            {localDirectory && (
              <div className="directory-browser" aria-label="本機資料夾瀏覽器">
                <div className="directory-browser-head">
                  <code>{localDirectory.path}</code>
                  <div>
                    <button
                      className="icon-text-button"
                      type="button"
                      disabled={!localDirectory.parentPath || isDirectoryLoading}
                      onClick={() => {
                        if (localDirectory.parentPath) {
                          void openFolderPicker(localDirectory.parentPath);
                        }
                      }}
                    >
                      上層
                    </button>
                    <button
                      className="primary-button"
                      type="button"
                      onClick={() => {
                        setForm({ ...form, workspacePath: localDirectory.path });
                        setLocalDirectory(null);
                      }}
                    >
                      選取此資料夾
                    </button>
                  </div>
                </div>
                {localDirectory.directories.length === 0 ? (
                  <p className="form-note">這個資料夾底下沒有可選擇的子資料夾</p>
                ) : (
                  <div className="directory-list">
                    {localDirectory.directories.map((directory) => (
                      <button
                        className="directory-row"
                        key={directory.path}
                        type="button"
                        onClick={() => openFolderPicker(directory.path)}
                      >
                        {directory.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {directoryError && <p className="form-message error">{directoryError}</p>}
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
        ) : (
          <Panel title="建立專案">
            {message && <p className="form-message success">{message}</p>}
            {error && <p className="form-message error">{error}</p>}
            <p className="form-note">按下建立專案後，選擇本機工作目錄與工作流，再確認參與角色。</p>
          </Panel>
        )}
      </div>
    </section>
  );
}
