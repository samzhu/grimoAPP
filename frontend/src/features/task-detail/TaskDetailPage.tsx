import { ChatCenteredText, Code, Play } from "@phosphor-icons/react";
import type { Task } from "../../domain/task/task-types";
import { stateTone } from "../../domain/task/task-selectors";
import { Badge } from "../../shared/ui/Badge";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

export function TaskDetailPage({
  task,
  onBack,
}: {
  task: Task;
  onBack: () => void;
}) {
  return (
    <section className="task-page">
      <div className="task-page-head">
        <div className="task-page-toolbar">
          <button className="icon-text-button" type="button" onClick={onBack}>
            ← 回到 Task 管理
          </button>
          <div className="task-actions">
            <button type="button" className="primary-button">
              {task.state === "READY" ? <Play /> : <ChatCenteredText />}
              {task.state === "READY" ? "開始執行" : "使用 Chat"}
            </button>
          </div>
        </div>
        <div className="task-page-title">
          <div className="task-page-meta">
            <Badge>{task.id}</Badge>
            <Badge tone={stateTone(task.state)}>{task.state}</Badge>
            <Badge>{task.step}</Badge>
            <Badge>品質 {task.score}</Badge>
          </div>
          <h1>{task.title}</h1>
          <p>{task.description}</p>
        </div>
      </div>
      <div className="task-page-layout">
        <div className="task-main-column">
          <Panel title="Acceptance">
            <ul className="clean-list">
              {task.acceptance.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </Panel>
          <Panel title="Evidence">
            <div className="evidence-list">
              {task.evidence.map((item) => (
                <span key={item}>
                  <Code />
                  {item}
                </span>
              ))}
            </div>
          </Panel>
        </div>
        <aside className="task-sidebar">
          <Panel title="Stage & Quality">
            <Metric label="目前階段" value={task.step} />
            <Metric label="品質狀態" value={`品質 ${task.score} / 10`} />
            <Metric label="下一步" value={task.gaps[0] ?? "等待人工審查或收尾"} />
          </Panel>
          {task.gaps.length > 0 && (
            <Panel title="待補缺口">
              <ul className="clean-list">
                {task.gaps.map((gap) => (
                  <li key={gap}>{gap}</li>
                ))}
              </ul>
            </Panel>
          )}
        </aside>
      </div>
    </section>
  );
}
