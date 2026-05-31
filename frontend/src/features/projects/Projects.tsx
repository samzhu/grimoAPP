import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { Project, WorkflowRecipe } from "../../domain/project/project-types";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";
import { createProject, listProjects, listWorkflowRecipes } from "./project-api";

type ProjectsProps = {
  onCurrentProjectChange: (project: Project) => void;
};

const emptyForm = {
  name: "",
  description: "",
  folderPath: "",
  workflowRecipeId: "coding",
};

export function Projects({ onCurrentProjectChange }: ProjectsProps) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [recipes, setRecipes] = useState<WorkflowRecipe[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let isMounted = true;
    Promise.all([listProjects(), listWorkflowRecipes()])
      .then(([projectList, recipeList]) => {
        if (!isMounted) {
          return;
        }
        setProjects(projectList);
        setRecipes(recipeList);
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

  const submitProject = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setMessage("");
    setIsSubmitting(true);
    try {
      const project = await createProject(form);
      setProjects((current) => [project, ...current]);
      onCurrentProjectChange(project);
      setForm(emptyForm);
      setMessage(`${project.name} 已新增並設為目前專案`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "新增專案失敗");
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
                  <Metric label="資料夾" value={project.folderPath} />
                  <Metric label="工作流" value={project.workflowRecipeName} />
                  <Metric label="狀態" value={project.status} />
                </button>
              ))}
            </div>
          )}
        </Panel>
        <Panel title="新增專案">
          <form className="form-stack" onSubmit={submitProject}>
            <label>
              名稱
              <input
                name="name"
                placeholder="例如 grimoAPP"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </label>
            <label>
              描述
              <textarea
                name="description"
                placeholder="這個專案的產品目標、主要工作類型或目前焦點"
                value={form.description}
                onChange={(event) => setForm({ ...form, description: event.target.value })}
              />
            </label>
            <label>
              專案資料夾
              <input
                name="folderPath"
                placeholder="/Users/samzhu/workspace/github-samzhu/grimoAPP"
                value={form.folderPath}
                onChange={(event) => setForm({ ...form, folderPath: event.target.value })}
              />
            </label>
            <label>
              專案工作流
              <select
                name="workflowRecipeId"
                value={form.workflowRecipeId}
                onChange={(event) => setForm({ ...form, workflowRecipeId: event.target.value })}
              >
                {recipes.length === 0 ? (
                  <option value="coding">開發工作流</option>
                ) : (
                  recipes.map((recipe) => (
                    <option key={recipe.id} value={recipe.id}>
                      {recipe.name}
                    </option>
                  ))
                )}
              </select>
            </label>
            {error && <p className="form-message error">{error}</p>}
            {message && <p className="form-message success">{message}</p>}
            <button type="submit" className="primary-button" disabled={isSubmitting}>
              {isSubmitting ? "建立中..." : "新增專案"}
            </button>
          </form>
        </Panel>
      </div>
    </section>
  );
}
