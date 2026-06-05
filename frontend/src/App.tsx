import { useEffect, useMemo, useReducer, useState } from "react";
import { List } from "@phosphor-icons/react";
import { Navigation } from "./app/Navigation";
import { RuntimeProvider } from "./app/RuntimeProvider";
import grimoLogoUrl from "./assets/grimo-logo.png";
import type { Project } from "./domain/project/project-types";
import { tasks } from "./domain/task/task-fixtures";
import { taskMatchesQuery } from "./domain/task/task-selectors";
import { Blockers } from "./features/blockers/Blockers";
import { Projects } from "./features/projects/Projects";
import { TaskWorkbench } from "./features/task-board/TaskWorkbench";
import { createTask, listTasks } from "./features/task-board/task-api";
import type { CreateTaskInput } from "./features/task-board/task-api";
import {
  createInitialTaskWorkbenchState,
  taskWorkbenchReducer,
} from "./features/task-board/task-workbench-reducer";
import { AssistantChat } from "./features/task-forming-chat/AssistantChat";
import { Workflow } from "./features/workflow/Workflow";

export function App() {
  const [currentProject, setCurrentProject] = useState<Project | null>(null);
  const [projectTasks, setProjectTasks] = useState(tasks);
  const [taskLoadError, setTaskLoadError] = useState("");
  const [workbench, dispatch] = useReducer(
    taskWorkbenchReducer,
    createInitialTaskWorkbenchState(),
  );

  useEffect(() => {
    if (!currentProject) {
      setProjectTasks(tasks);
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
  const submitTask = async (input: CreateTaskInput) => {
    if (!currentProject) {
      throw new Error("請先選擇 Project");
    }
    const task = await createTask(currentProject.id, input);
    setProjectTasks((current) => [task, ...current]);
  };

  return (
    <RuntimeProvider>
      <div className="app-shell">
        <header className="topbar">
          <button
            className="topbar-menu"
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
          <div className="project-context">
            <span>目前專案</span>
            <strong>{currentProject?.name ?? "grimo/web"}</strong>
            <code>{currentProject?.projectPath ?? "/Users/samzhu/workspace/github-samzhu/grimo/apps/web"}</code>
          </div>
        </header>

        <div className={workspaceClassName}>
          {workbench.isNavOpen && (
            <Navigation
              active={workbench.view}
              isPinned={workbench.isNavPinned}
              onSelect={(nextView) => {
                dispatch({ type: "view.selected", view: nextView });
              }}
              onClose={() => dispatch({ type: "nav.closed" })}
              onTogglePin={() => dispatch({ type: "nav.pinToggled" })}
            />
          )}
          <main className="main-surface">
            {workbench.view === "tasks" && (
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
            )}
            {workbench.view === "blockers" && <Blockers tasks={projectTasks} onOpenChat={openTaskChat} />}
            {workbench.view === "projects" && <Projects onCurrentProjectChange={setCurrentProject} />}
            {workbench.view === "chat" && <AssistantChat selectedTask={selectedTask} />}
            {workbench.view === "workflow" && <Workflow />}
          </main>
        </div>
      </div>
    </RuntimeProvider>
  );
}
