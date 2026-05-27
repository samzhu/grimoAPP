import { useMemo, useReducer } from "react";
import { List } from "@phosphor-icons/react";
import { Navigation } from "./app/Navigation";
import { RuntimeProvider } from "./app/RuntimeProvider";
import { tasks } from "./domain/task/task-fixtures";
import { taskMatchesQuery } from "./domain/task/task-selectors";
import { Blockers } from "./features/blockers/Blockers";
import { Projects } from "./features/projects/Projects";
import { TaskWorkbench } from "./features/task-board/TaskWorkbench";
import {
  createInitialTaskWorkbenchState,
  taskWorkbenchReducer,
} from "./features/task-board/task-workbench-reducer";
import { AssistantChat } from "./features/task-forming-chat/AssistantChat";
import { Workflow } from "./features/workflow/Workflow";

export function App() {
  const [workbench, dispatch] = useReducer(
    taskWorkbenchReducer,
    tasks[0].id,
    createInitialTaskWorkbenchState,
  );

  const filteredTasks = useMemo(
    () => tasks.filter((task) => taskMatchesQuery(task, workbench.query)),
    [workbench.query],
  );

  const selectedTask =
    tasks.find((task) => task.id === workbench.selectedTaskId) ?? tasks[0];

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
          <div className="brand-mark">G</div>
          <div className="brand-copy">
            <strong>Grimo</strong>
          </div>
          <div className="project-context">
            <span>目前專案</span>
            <strong>grimo/web</strong>
            <code>/Users/samzhu/workspace/github-samzhu/grimo/apps/web</code>
          </div>
        </header>

        <div className={workbench.isNavOpen ? "workspace-shell nav-open" : "workspace-shell"}>
          {workbench.isNavOpen && (
            <Navigation
              active={workbench.view}
              onSelect={(nextView) => {
                dispatch({ type: "view.selected", view: nextView });
              }}
              onClose={() => dispatch({ type: "nav.closed" })}
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
                isCreateTaskOpen={workbench.isCreateTaskOpen}
                isTaskPageOpen={workbench.isTaskPageOpen}
                onQueryChange={(query) => dispatch({ type: "query.changed", query })}
                onSelectTask={(taskId) => {
                  dispatch({ type: "task.selected", taskId });
                }}
                onCloseDetail={() => dispatch({ type: "detail.closed" })}
                onToggleDetailPin={() => dispatch({ type: "detail.pinToggled" })}
                onOpenTaskPage={() => dispatch({ type: "taskPage.opened" })}
                onCloseTaskPage={() => dispatch({ type: "taskPage.closed" })}
                onOpenCreateTask={() => dispatch({ type: "createTask.opened" })}
                onCloseCreateTask={() => dispatch({ type: "createTask.closed" })}
              />
            )}
            {workbench.view === "blockers" && <Blockers tasks={tasks} />}
            {workbench.view === "projects" && <Projects />}
            {workbench.view === "chat" && <AssistantChat selectedTask={selectedTask} />}
            {workbench.view === "workflow" && <Workflow />}
          </main>
        </div>
      </div>
    </RuntimeProvider>
  );
}
