import { MagnifyingGlass } from "@phosphor-icons/react";
import type { Task } from "../../domain/task/task-types";
import { stateColumns } from "../../domain/task/task-fixtures";
import { Badge } from "../../shared/ui/Badge";
import { CreateTaskDialog } from "../task-create/CreateTaskDialog";
import { TaskDetail } from "../task-detail/TaskDetail";
import { TaskDetailPage } from "../task-detail/TaskDetailPage";

export function TaskWorkbench({
  filteredTasks,
  query,
  selectedTask,
  isDetailOpen,
  isDetailPinned,
  isCreateTaskOpen,
  isTaskPageOpen,
  onQueryChange,
  onSelectTask,
  onCloseDetail,
  onToggleDetailPin,
  onOpenTaskPage,
  onCloseTaskPage,
  onOpenCreateTask,
  onCloseCreateTask,
}: {
  filteredTasks: Task[];
  query: string;
  selectedTask: Task;
  isDetailOpen: boolean;
  isDetailPinned: boolean;
  isCreateTaskOpen: boolean;
  isTaskPageOpen: boolean;
  onQueryChange: (query: string) => void;
  onSelectTask: (taskId: string) => void;
  onCloseDetail: () => void;
  onToggleDetailPin: () => void;
  onOpenTaskPage: () => void;
  onCloseTaskPage: () => void;
  onOpenCreateTask: () => void;
  onCloseCreateTask: () => void;
}) {
  if (isTaskPageOpen) {
    return <TaskDetailPage task={selectedTask} onBack={onCloseTaskPage} />;
  }

  return (
    <section
      className={
        isDetailOpen && isDetailPinned
          ? "task-workbench detail-pinned"
          : "task-workbench"
      }
    >
      <div className="board-pane">
        <div className="section-head">
          <div>
            <h1>任務工作台</h1>
            <p>從 Chat、CLI 或 issue 進來的工作先成為任務，再由 Grimo 管理定義、執行與審查</p>
          </div>
          <div className="toolbar">
            <label className="search-field">
              <MagnifyingGlass />
              <input
                value={query}
                onChange={(event) => onQueryChange(event.target.value)}
                placeholder="搜尋任務 / 來源 / 關鍵字"
              />
            </label>
            <button className="create-task-button" type="button" onClick={onOpenCreateTask}>
              新增 Task
            </button>
          </div>
        </div>

        <div className="board-grid" aria-label="Task board">
          {stateColumns.map((state) => {
            const columnTasks = filteredTasks.filter((task) => task.state === state);
            return (
              <section className="board-column" key={state}>
                <div className="column-head">
                  <strong>{state}</strong>
                  <span>任務 <b>{columnTasks.length}</b></span>
                </div>
                <div className="column-body">
                  {columnTasks.map((task) => (
                    <button
                      className={
                        selectedTask.id === task.id ? "task-card selected" : "task-card"
                      }
                      key={task.id}
                      type="button"
                      aria-current={selectedTask.id === task.id ? "true" : undefined}
                      onClick={() => onSelectTask(task.id)}
                    >
                      <span className="task-id">{task.id}</span>
                      <strong>{task.title}</strong>
                      <div className="task-card-foot">
                        {task.labels.slice(0, 2).map((label) => (
                          <Badge key={label}>{label}</Badge>
                        ))}
                      </div>
                      <div className="task-meta-row">
                        <span>updated {task.updatedAt}</span>
                        {task.comments > 0 && <span>□ {task.comments}</span>}
                      </div>
                    </button>
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      </div>

      {isDetailOpen && (
        <TaskDetail
          task={selectedTask}
          isPinned={isDetailPinned}
          onClose={onCloseDetail}
          onOpenFullPage={onOpenTaskPage}
          onTogglePin={onToggleDetailPin}
        />
      )}
      {isCreateTaskOpen && <CreateTaskDialog onClose={onCloseCreateTask} />}
    </section>
  );
}
