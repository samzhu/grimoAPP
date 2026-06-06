import type { Project } from "../../domain/project/project-types";

type ProjectSelectionGateReason = "first-run" | "closed" | "missing-session" | "no-context";

type ProjectSelectionGateProps = {
  projects: Project[];
  reason: ProjectSelectionGateReason;
  message?: string;
  onSelectProject: (project: Project) => void;
  onCreateProject: () => void;
  onRetry?: () => void;
};

function shortProjectPath(projectPath: string) {
  const segments = projectPath.split("/").filter(Boolean);
  return segments.length > 2
    ? `.../${segments.slice(-2).join("/")}`
    : projectPath;
}

export function ProjectSelectionGate({
  projects,
  reason,
  message,
  onSelectProject,
  onCreateProject,
  onRetry,
}: ProjectSelectionGateProps) {
  const isFirstRun = reason === "first-run" || projects.length === 0;
  const heading = isFirstRun ? "建立第一個 Project" : "選擇或建立 Project";
  const copy =
    message ||
    (isFirstRun
      ? "Project 會讓 Task 工作台有真實 repo / codebase context。"
      : "Project 會決定 Task 的工作流、角色和品質基準。");

  return (
    <section className="project-selection-gate" aria-labelledby="project-selection-title">
      <div className="project-selection-inner">
        <div className="section-head project-selection-head">
          <div>
            <h1 id="project-selection-title">{heading}</h1>
            <p>{copy}</p>
          </div>
          {onRetry ? (
            <button className="primary-button" type="button" onClick={onRetry}>
              重試
            </button>
          ) : (
            <button
              className={isFirstRun ? "primary-button" : "icon-text-button"}
              type="button"
              onClick={onCreateProject}
            >
              建立 Project
            </button>
          )}
        </div>

        {!isFirstRun && (
          <div className="project-selection-list" aria-label="Project list">
            {projects.map((project) => (
              <button
                className="project-list-card project-selection-card"
                key={project.id}
                type="button"
                onClick={() => onSelectProject(project)}
              >
                <strong>{project.name}</strong>
                <span>{shortProjectPath(project.projectPath)}</span>
                <small>{project.workflowRecipeName}</small>
              </button>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
