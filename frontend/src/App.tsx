import { useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { List } from "@phosphor-icons/react";
import { Navigation } from "./app/Navigation";
import { RuntimeProvider } from "./app/RuntimeProvider";
import grimoLogoUrl from "./assets/grimo-logo.png";
import type { Project } from "./domain/project/project-types";
import type { Task } from "./domain/task/task-types";
import { taskMatchesQuery } from "./domain/task/task-selectors";
import { Blockers } from "./features/blockers/Blockers";
import { Projects } from "./features/projects/Projects";
import { ProjectSelectionGate } from "./features/projects/ProjectSelectionGate";
import { ProjectSwitcher } from "./features/projects/ProjectSwitcher";
import { listProjects } from "./features/projects/project-api";
import {
  readProjectSession,
  saveClosedProjectSession,
  saveOpenProjectSession,
} from "./features/projects/project-session";
import { TaskWorkbench } from "./features/task-board/TaskWorkbench";
import { createTask, listTasks } from "./features/task-board/task-api";
import type { CreateTaskInput } from "./features/task-board/task-api";
import {
  createInitialTaskWorkbenchState,
  taskWorkbenchReducer,
} from "./features/task-board/task-workbench-reducer";
import { AssistantChat } from "./features/task-forming-chat/AssistantChat";
import { Workflow } from "./features/workflow/Workflow";

type ProjectBootstrapStatus = "loading" | "ready" | "error";
type ProjectGateReason = "first-run" | "closed" | "missing-session" | "no-context";

export function App() {
  const [currentProject, setCurrentProject] = useState<Project | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectTasks, setProjectTasks] = useState<Task[]>([]);
  const [projectBootstrapStatus, setProjectBootstrapStatus] =
    useState<ProjectBootstrapStatus>("loading");
  const [projectGateReason, setProjectGateReason] = useState<ProjectGateReason>("first-run");
  const [projectGateMessage, setProjectGateMessage] = useState(
    "Project 會讓 Task 工作台有真實 repo / codebase context。",
  );
  const [projectViewMode, setProjectViewMode] = useState<"list" | "create">("list");
  const [taskLoadError, setTaskLoadError] = useState("");
  const [workbench, dispatch] = useReducer(
    taskWorkbenchReducer,
    createInitialTaskWorkbenchState(),
  );

  const openProject = useCallback((project: Project) => {
    setCurrentProject(project);
    saveOpenProjectSession(project.id);
    setProjectGateReason("no-context");
    setProjectGateMessage("Project 會讓 Task 工作台有真實 repo / codebase context。");
    dispatch({ type: "view.selected", view: "tasks" });
  }, []);

  const bootstrapProjects = useCallback(() => {
    let isMounted = true;
    setProjectBootstrapStatus("loading");
    listProjects()
      .then((loadedProjects) => {
        if (!isMounted) {
          return;
        }
        setProjects(loadedProjects);
        setProjectBootstrapStatus("ready");
        if (loadedProjects.length === 0) {
          setCurrentProject(null);
          setProjectTasks([]);
          setProjectGateReason("first-run");
          setProjectGateMessage(
            "Project 會讓 Task 工作台有真實 repo / codebase context。",
          );
          return;
        }

        const session = readProjectSession();
        if (session.isClosed || !session.lastActiveProjectId) {
          setCurrentProject(null);
          setProjectTasks([]);
          setProjectGateReason(session.isClosed ? "closed" : "missing-session");
          setProjectGateMessage("Project 會決定 Task 的工作流、角色和品質基準。");
          return;
        }

        const restoredProject = loadedProjects.find(
          (project) => project.id === session.lastActiveProjectId,
        );
        if (restoredProject) {
          openProject(restoredProject);
          return;
        }

        setCurrentProject(null);
        setProjectTasks([]);
        setProjectGateReason("missing-session");
        setProjectGateMessage(
          "上次開啟的 Project 已不存在或無法載入，請選擇 Project。",
        );
      })
      .catch((caught: unknown) => {
        if (isMounted) {
          setProjectBootstrapStatus("error");
          setCurrentProject(null);
          setProjectTasks([]);
          setProjectGateReason("no-context");
          setProjectGateMessage(
            caught instanceof Error ? caught.message : "Project 載入失敗",
          );
        }
      });
    return () => {
      isMounted = false;
    };
  }, [openProject]);

  useEffect(() => bootstrapProjects(), [bootstrapProjects]);

  useEffect(() => {
    if (!currentProject) {
      setProjectTasks([]);
      setTaskLoadError("");
      return;
    }
    let isMounted = true;
    listTasks(currentProject.id)
      .then((loadedTasks) => {
        if (isMounted) {
          setProjectTasks(loadedTasks);
          setTaskLoadError("");
        }
      })
      .catch((caught: unknown) => {
        if (isMounted) {
          setProjectTasks([]);
          setTaskLoadError(caught instanceof Error ? caught.message : "Task 載入失敗");
        }
      });
    return () => {
      isMounted = false;
    };
  }, [currentProject]);

  const filteredTasks = useMemo(
    () => projectTasks.filter((task) => taskMatchesQuery(task, workbench.query)),
    [projectTasks, workbench.query],
  );

  const selectedTask = workbench.selectedTaskId
    ? projectTasks.find((task) => task.id === workbench.selectedTaskId) ?? null
    : null;
  const workspaceClassName = [
    "workspace-shell",
    workbench.isNavOpen ? "nav-open" : "",
    workbench.isNavOpen && workbench.isNavPinned ? "nav-pinned" : "",
  ]
    .filter(Boolean)
    .join(" ");
  const openTaskChat = (taskId: string) => {
    dispatch({ type: "task.selected", taskId });
    dispatch({ type: "view.selected", view: "chat" });
  };
  const selectProject = (project: Project) => {
    openProject(project);
  };
  const startProjectCreation = () => {
    setProjectViewMode("create");
    dispatch({ type: "view.selected", view: "projects" });
  };
  const manageProjects = () => {
    setProjectViewMode("list");
    dispatch({ type: "view.selected", view: "projects" });
  };
  const syncProjects = (nextProjects: Project[]) => {
    setProjects(nextProjects);
  };
  const closeProject = () => {
    saveClosedProjectSession(currentProject?.id ?? null);
    setCurrentProject(null);
    setProjectTasks([]);
    setTaskLoadError("");
    setProjectGateReason("closed");
    setProjectGateMessage("Project 會決定 Task 的工作流、角色和品質基準。");
    dispatch({ type: "view.selected", view: "tasks" });
  };
  const submitTask = async (input: CreateTaskInput) => {
    if (!currentProject) {
      throw new Error("請先選擇 Project");
    }
    const task = await createTask(currentProject.id, input);
    setProjectTasks((current) => [task, ...current]);
  };
  const shouldShowProjectGate =
    projectBootstrapStatus === "error" ||
    (!currentProject && !["projects", "workflow"].includes(workbench.view));
  const projectGate = (
    <ProjectSelectionGate
      projects={projects}
      reason={projectGateReason}
      message={projectGateMessage}
      onSelectProject={selectProject}
      onCreateProject={startProjectCreation}
      onRetry={projectBootstrapStatus === "error" ? bootstrapProjects : undefined}
    />
  );

  return (
    <RuntimeProvider>
      <div className="app-shell">
        <header className="app-header">
          <button
            className="app-header-menu"
            type="button"
            aria-expanded={workbench.isNavOpen}
            aria-label={workbench.isNavOpen ? "收合主選單" : "展開主選單"}
            onClick={() => dispatch({ type: "nav.toggled" })}
          >
            <List />
          </button>
          <div className="brand-mark" aria-hidden="true">
            <img src={grimoLogoUrl} alt="" />
          </div>
          <div className="brand-copy">
            <strong>Grimo</strong>
          </div>
          {projectBootstrapStatus === "loading" ? (
            <div className="project-context">
              <span>目前專案</span>
              <strong>載入 Project context</strong>
            </div>
          ) : (
            <ProjectSwitcher
              currentProject={currentProject}
              projects={projects}
              onSelectProject={selectProject}
              onCreateProject={startProjectCreation}
              onManageProjects={manageProjects}
              onCloseProject={closeProject}
            />
          )}
        </header>

        <div className={workspaceClassName}>
          {workbench.isNavOpen && (
            <Navigation
              active={workbench.view}
              isPinned={workbench.isNavPinned}
              onSelect={(nextView) => {
                if (nextView === "projects") {
                  setProjectViewMode("list");
                }
                dispatch({ type: "view.selected", view: nextView });
              }}
              onClose={() => dispatch({ type: "nav.closed" })}
              onTogglePin={() => dispatch({ type: "nav.pinToggled" })}
            />
          )}
          <main className="main-content-area">
            {shouldShowProjectGate ? projectGate : null}
            {!shouldShowProjectGate && workbench.view === "tasks" && (
              currentProject ? (
              <TaskWorkbench
                filteredTasks={filteredTasks}
                query={workbench.query}
                selectedTask={selectedTask}
                isDetailOpen={workbench.isDetailOpen}
                isDetailPinned={workbench.isDetailPinned}
                isFocusCollapsed={workbench.isFocusCollapsed}
                isCreateTaskOpen={workbench.isCreateTaskOpen}
                isTaskPageOpen={workbench.isTaskPageOpen}
                onQueryChange={(query) => dispatch({ type: "query.changed", query })}
                onSelectTask={(taskId) => {
                  dispatch({ type: "task.selected", taskId });
                }}
                onCloseDetail={() => dispatch({ type: "detail.closed" })}
                onToggleDetailPin={() => dispatch({ type: "detail.pinToggled" })}
                onToggleFocus={() => dispatch({ type: "focus.toggled" })}
                onOpenTaskPage={() => dispatch({ type: "taskPage.opened" })}
                onCloseTaskPage={() => dispatch({ type: "taskPage.closed" })}
                onOpenCreateTask={() => dispatch({ type: "createTask.opened" })}
                onCloseCreateTask={() => dispatch({ type: "createTask.closed" })}
                onCreateTask={submitTask}
                onOpenChat={openTaskChat}
                canCreateTask={Boolean(currentProject)}
                taskLoadError={taskLoadError}
              />
              ) : null
            )}
            {!shouldShowProjectGate && workbench.view === "blockers" && <Blockers tasks={projectTasks} onOpenChat={openTaskChat} />}
            {!shouldShowProjectGate && workbench.view === "projects" && (
              <Projects
                initialViewMode={projectViewMode}
                projects={projects}
                onProjectsChange={syncProjects}
                onCurrentProjectChange={selectProject}
              />
            )}
            {!shouldShowProjectGate && workbench.view === "chat" && <AssistantChat selectedTask={selectedTask} />}
            {!shouldShowProjectGate && workbench.view === "workflow" && <Workflow />}
          </main>
        </div>
      </div>
    </RuntimeProvider>
  );
}
