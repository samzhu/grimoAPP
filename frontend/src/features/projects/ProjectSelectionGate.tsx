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

type ProjectSetupHeroProps = {
  heading: string;
  copy: string;
  actionLabel: string;
  actionVariant: "primary" | "secondary";
  onAction: () => void;
};

function shortProjectPath(projectPath: string) {
  const segments = projectPath.split("/").filter(Boolean);
  return segments.length > 2
    ? `.../${segments.slice(-2).join("/")}`
    : projectPath;
}

function ProjectSetupHero({
  heading,
  copy,
  actionLabel,
  actionVariant,
  onAction,
}: ProjectSetupHeroProps) {
  return (
    <div className="project-setup-hero">
      <div>
        <h1 id="project-selection-title">{heading}</h1>
        <p>{copy}</p>
      </div>
      <button
        className={actionVariant === "primary" ? "primary-button" : "icon-text-button"}
        type="button"
        onClick={onAction}
      >
        {actionLabel}
      </button>
    </div>
  );
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
  const isError = Boolean(onRetry);
  const heading = isError
    ? "無法載入 Project context"
    : isFirstRun ? "建立第一個 Project" : "選擇或建立 Project";
  const copy =
    message ||
    (isError
      ? "Project 載入失敗，請重試。"
      : isFirstRun
      ? "Project 會讓 Task 工作台有真實 repo / codebase context。"
      : "Project 會決定 Task 的工作流、角色和品質基準。");

  return (
    <section className="project-selection-gate" aria-labelledby="project-selection-title">
      <div className="project-selection-inner">
        <ProjectSetupHero
          heading={heading}
          copy={copy}
          actionLabel={isError ? "重試" : "建立 Project"}
          actionVariant={isError || isFirstRun ? "primary" : "secondary"}
          onAction={onRetry ?? onCreateProject}
        />

        {!isError && !isFirstRun && (
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
