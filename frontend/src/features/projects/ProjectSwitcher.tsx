import { CaretDown, FolderOpen, Plus, X } from "@phosphor-icons/react";
import { useState } from "react";
import type { Project } from "../../domain/project/project-types";

type ProjectSwitcherProps = {
  currentProject: Project | null;
  projects: Project[];
  onSelectProject: (project: Project) => void;
  onCreateProject: () => void;
  onManageProjects: () => void;
  onCloseProject: () => void;
};

function shortProjectPath(projectPath: string) {
  const segments = projectPath.split("/").filter(Boolean);
  return segments.length > 2
    ? `.../${segments.slice(-2).join("/")}`
    : projectPath;
}

export function ProjectSwitcher({
  currentProject,
  projects,
  onSelectProject,
  onCreateProject,
  onManageProjects,
  onCloseProject,
}: ProjectSwitcherProps) {
  const [isOpen, setIsOpen] = useState(false);

  if (!currentProject) {
    return (
      <div className="project-context">
        <span>目前專案</span>
        <strong>尚未開啟 Project</strong>
      </div>
    );
  }

  const chooseProject = (project: Project) => {
    setIsOpen(false);
    onSelectProject(project);
  };
  const runAction = (action: () => void) => {
    setIsOpen(false);
    action();
  };

  return (
    <div className="project-switcher">
      <button
        className="project-context project-switcher-trigger"
        type="button"
        aria-expanded={isOpen}
        aria-label={`目前專案 ${currentProject.name}`}
        onClick={() => setIsOpen((current) => !current)}
      >
        <span>目前專案</span>
        <strong>{currentProject.name}</strong>
        <code>{currentProject.projectPath}</code>
        <CaretDown aria-hidden="true" />
      </button>

      {isOpen && (
        <div className="project-switcher-menu" aria-label="Project Switcher">
          <div className="project-switcher-list">
            {projects.map((project) => (
              <button
                className="project-switcher-item"
                key={project.id}
                type="button"
                aria-current={project.id === currentProject.id ? "true" : undefined}
                onClick={() => chooseProject(project)}
              >
                <strong>{project.name}</strong>
                <span>{shortProjectPath(project.projectPath)}</span>
                <small>{project.workflowRecipeName}</small>
              </button>
            ))}
          </div>
          <div className="project-switcher-actions">
            <button type="button" onClick={() => runAction(onCreateProject)}>
              <Plus aria-hidden="true" />
              新增 Project
            </button>
            <button type="button" onClick={() => runAction(onManageProjects)}>
              <FolderOpen aria-hidden="true" />
              管理 Projects
            </button>
            <button type="button" onClick={() => runAction(onCloseProject)}>
              <X aria-hidden="true" />
              Close Project
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
