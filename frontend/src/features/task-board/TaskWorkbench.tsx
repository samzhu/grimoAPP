import {
  CaretDown,
  CaretUp,
  ChatCircleText,
  MagnifyingGlass,
  Plus,
  WarningCircle,
} from "@phosphor-icons/react";
import type { Task, TaskState } from "../../domain/task/task-types";
import { stateColumns } from "../../domain/task/task-fixtures";
import { Badge } from "../../shared/ui/Badge";
import { CreateTaskDialog } from "../task-create/CreateTaskDialog";
import type { CreateTaskInput } from "./task-api";
import { TaskDetail } from "../task-detail/TaskDetail";
import { TaskDetailPage } from "../task-detail/TaskDetailPage";

const mobileListStates: TaskState[] = [...stateColumns];

function getAttentionLabel(task: Task) {
  if (task.state === "REVIEW") {
    return "等待人工審查";
  }

  if (task.state === "READY" || task.state === "RUNNING") {
    return "需要修復條件";
  }

  return "需要補齊定義";
}

export function TaskWorkbench({
  filteredTasks,
  query,
  selectedTask,
  isDetailOpen,
  isDetailPinned,
  isFocusCollapsed,
  isCreateTaskOpen,
  isTaskPageOpen,
  onQueryChange,
  onSelectTask,
  onCloseDetail,
  onToggleDetailPin,
  onToggleFocus,
  onOpenTaskPage,
  onCloseTaskPage,
  onOpenCreateTask,
  onCloseCreateTask,
  onCreateTask,
  onOpenChat,
  canCreateTask,
  taskLoadError,
}: {
  filteredTasks: Task[];
  query: string;
  selectedTask: Task | null;
  isDetailOpen: boolean;
  isDetailPinned: boolean;
  isFocusCollapsed: boolean;
  isCreateTaskOpen: boolean;
  isTaskPageOpen: boolean;
  onQueryChange: (query: string) => void;
  onSelectTask: (taskId: string) => void;
  onCloseDetail: () => void;
  onToggleDetailPin: () => void;
  onToggleFocus: () => void;
  onOpenTaskPage: () => void;
  onCloseTaskPage: () => void;
  onOpenCreateTask: () => void;
  onCloseCreateTask: () => void;
  onCreateTask: (input: CreateTaskInput) => Promise<void>;
  onOpenChat: (taskId: string) => void;
  canCreateTask: boolean;
  taskLoadError: string;
}) {
  const reviewTasks = filteredTasks.filter((task) => task.state === "REVIEW");
  const repairTasks = filteredTasks.filter(
    (task) => (task.state === "READY" || task.state === "RUNNING") && task.gaps.length > 0,
  );
  const attentionTasks = [...reviewTasks, ...repairTasks];
  const hasAttentionTasks = attentionTasks.length > 0;

  if (isTaskPageOpen) {
    return selectedTask ? (
      <TaskDetailPage
        task={selectedTask}
        onBack={onCloseTaskPage}
        onOpenChat={() => onOpenChat(selectedTask.id)}
      />
    ) : null;
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
                placeholder="搜尋任務 / 關鍵字"
              />
            </label>
            <button
              className="create-task-button"
              type="button"
              disabled={!canCreateTask}
              onClick={onOpenCreateTask}
            >
              <Plus aria-hidden="true" />
              新增 Task
            </button>
          </div>
        </div>
        {taskLoadError && <p className="form-message error">{taskLoadError}</p>}

        {hasAttentionTasks && (
          <section
            className={isFocusCollapsed ? "focus-strip collapsed" : "focus-strip"}
            aria-labelledby="focus-strip-title"
          >
            <div className="focus-strip-head">
              <div className="focus-title-row">
                <h2 id="focus-strip-title">待處理焦點</h2>
                <span className="focus-kicker">
                  <WarningCircle aria-hidden="true" />
                  需要你處理
                </span>
              </div>
              <div className="focus-strip-actions">
                <span>{attentionTasks.length} 個任務</span>
                <button
                  className="focus-toggle"
                  type="button"
                  aria-expanded={!isFocusCollapsed}
                  aria-controls="focus-task-grid"
                  onClick={onToggleFocus}
                >
                  {isFocusCollapsed ? (
                    <CaretDown aria-hidden="true" />
                  ) : (
                    <CaretUp aria-hidden="true" />
                  )}
                  {isFocusCollapsed ? "展開" : "收合"}
                </button>
              </div>
            </div>
            {!isFocusCollapsed && (
              <div className="focus-task-grid" id="focus-task-grid">
                {attentionTasks.slice(0, 3).map((task) => (
                  <article
                    className={
                      selectedTask?.id === task.id
                        ? "focus-task-card selected"
                        : "focus-task-card"
                    }
                    key={task.id}
                  >
                    <button
                      className="focus-task-main"
                      type="button"
                      aria-label={`查看焦點任務 ${task.id}`}
                      onClick={() => onSelectTask(task.id)}
                    >
                      <span className="task-id">{task.id}</span>
                      <strong>{task.title}</strong>
                      <div className="focus-task-meta">
                        <span>{getAttentionLabel(task)}</span>
                        <b>{task.score.toFixed(1)}</b>
                      </div>
                      <div className="focus-progress" aria-hidden="true">
                        <span style={{ width: `${Math.min(task.score * 10, 100)}%` }} />
                      </div>
                      <div className="task-card-foot">
                        {task.labels.slice(0, 2).map((label) => (
                          <Badge key={label} kind="label">
                            {label}
                          </Badge>
                        ))}
                      </div>
                    </button>
                    <div className="focus-actions">
                      <button type="button" onClick={() => onOpenChat(task.id)}>
                        <ChatCircleText aria-hidden="true" />
                        Chat
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}

        <div
          className={
            hasAttentionTasks
              ? `board-grid with-focus${isFocusCollapsed ? " focus-collapsed" : ""}`
              : "board-grid"
          }
          aria-label="Task board"
        >
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
                        selectedTask?.id === task.id ? "task-card selected" : "task-card"
                      }
                      key={task.id}
                      type="button"
                      aria-current={selectedTask?.id === task.id ? "true" : undefined}
                      onClick={() => onSelectTask(task.id)}
                    >
                      <span className="task-id">{task.id}</span>
                      <strong>{task.title}</strong>
                      <div className="task-card-foot">
                        {task.labels.slice(0, 2).map((label) => (
                          <Badge key={label} kind="label">
                            {label}
                          </Badge>
                        ))}
                      </div>
                      <div className="task-meta-row">
                        <span>updated {task.updatedAt}</span>
                        {task.commentCount > 0 && (
                          <span
                            className="task-comment-count"
                            aria-label={`${task.commentCount} 則留言`}
                          >
                            <ChatCircleText aria-hidden="true" />
                            {task.commentCount}
                          </span>
                        )}
                      </div>
                    </button>
                  ))}
                </div>
              </section>
            );
          })}
        </div>

        <section className="mobile-task-list" aria-label="Task list">
          <div className="mobile-list-head">
            <h2>工作列表</h2>
            <span>{filteredTasks.length} 個任務</span>
          </div>
          {mobileListStates.map((state) => {
            const listTasks = filteredTasks.filter((task) => task.state === state);

            if (listTasks.length === 0) {
              return null;
            }

            return (
              <section className="mobile-list-group" key={state}>
                <div className="mobile-list-group-head">
                  <strong>{state}</strong>
                  <span>{listTasks.length}</span>
                </div>
                <div className="mobile-list-items">
                  {listTasks.map((task) => (
                    <button
                      className={
                        selectedTask?.id === task.id
                          ? "mobile-task-row selected"
                          : "mobile-task-row"
                      }
                      key={task.id}
                      type="button"
                      aria-current={selectedTask?.id === task.id ? "true" : undefined}
                      onClick={() => onSelectTask(task.id)}
                    >
                      <span className="mobile-state-badge">{task.state}</span>
                      <span className="task-id">{task.id}</span>
                      <strong>{task.title}</strong>
                      <div className="task-card-foot">
                        {task.labels.slice(0, 2).map((label) => (
                          <Badge key={label} kind="label">
                            {label}
                          </Badge>
                        ))}
                      </div>
                      <div className="mobile-task-meta">
                        <span>updated {task.updatedAt}</span>
                        <b>{task.score > 0 ? task.score.toFixed(1) : "未評分"}</b>
                        {task.commentCount > 0 && (
                          <span
                            className="task-comment-count"
                            aria-label={`${task.commentCount} 則留言`}
                          >
                            <ChatCircleText aria-hidden="true" />
                            {task.commentCount}
                          </span>
                        )}
                      </div>
                    </button>
                  ))}
                </div>
              </section>
            );
          })}
        </section>
      </div>

      {isDetailOpen && selectedTask && (
        <TaskDetail
          task={selectedTask}
          isPinned={isDetailPinned}
          onClose={onCloseDetail}
          onOpenFullPage={onOpenTaskPage}
          onTogglePin={onToggleDetailPin}
          onOpenChat={() => onOpenChat(selectedTask.id)}
        />
      )}
      {isCreateTaskOpen && (
        <CreateTaskDialog
          onClose={onCloseCreateTask}
          onSubmit={onCreateTask}
        />
      )}
    </section>
  );
}
