import {
  ArrowLeft,
  ChatCenteredText,
  CheckCircle,
  Code,
  GitPullRequest,
  Play,
  WarningCircle,
  XCircle,
} from "@phosphor-icons/react";
import type { Task } from "../../domain/task/task-types";
import { stateTone } from "../../domain/task/task-selectors";
import { Badge } from "../../shared/ui/Badge";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

export function TaskDetailPage({
  task,
  onBack,
  onOpenChat,
}: {
  task: Task;
  onBack: () => void;
  onOpenChat: () => void;
}) {
  const isReview = task.state === "REVIEW";
  const gateStatus = task.score >= 9 ? "已達 Quality Gate" : "缺少通過證據";
  const nextAction = task.gaps[0] ?? (isReview ? "等待人工核准或退回" : "回到 Chat 繼續探索或規劃");
  const reviewChecklist = [
    "Definition Package 已確認",
    "執行證據已附上",
    "風險與退回路徑已列出",
  ];

  return (
    <section className="task-page">
      <div className="task-page-head">
        <div className="task-page-toolbar">
          <button className="icon-text-button" type="button" onClick={onBack}>
            <ArrowLeft aria-hidden="true" />
            回到 Task 管理
          </button>
          <div className="task-actions">
            <button
              type="button"
              className="primary-button"
              onClick={task.state === "READY" ? undefined : onOpenChat}
            >
              {task.state === "READY" ? <Play /> : <ChatCenteredText />}
              {task.state === "READY" ? "開始執行" : "Chat"}
            </button>
          </div>
        </div>
        <div className="task-page-title">
          <div className="task-page-meta">
            <Badge kind="task-id">{task.id}</Badge>
            <Badge kind="state" tone={stateTone(task.state)}>
              {task.state}
            </Badge>
            <Badge kind="metric">品質 {task.score}</Badge>
          </div>
          <h1>{task.title}</h1>
          <p>{task.description}</p>
        </div>
      </div>

      <div className="review-summary">
        <div>
          <span>目前狀態</span>
          <strong>{isReview ? "等待人工審查" : task.state}</strong>
        </div>
        <div>
          <span>Quality Gate</span>
          <strong>{gateStatus}</strong>
        </div>
        <div>
          <span>下一步</span>
          <strong>{nextAction}</strong>
        </div>
      </div>

      <div className="task-page-layout">
        <div className="task-main-column">
          {isReview && (
            <section className="review-decision-panel">
              <div>
                <Badge tone="warn">Human Gate</Badge>
                <h2>審查結論</h2>
                <p>
                  這個 Task 已進入 REVIEW。請根據驗收條件、evidence package 與待補缺口決定 approve 或 reject。
                </p>
              </div>
              <div className="review-decision-actions">
                <button type="button" className="primary-button">
                  <CheckCircle aria-hidden="true" />
                  Approve
                </button>
                <button type="button" className="danger-button">
                  <XCircle aria-hidden="true" />
                  Reject
                </button>
              </div>
            </section>
          )}

          <Panel title={isReview ? "Review Materials" : "Acceptance Gate"}>
            <ul className="clean-list">
              {task.acceptance.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </Panel>

          <Panel title="Evidence Package">
            <div className="evidence-list">
              {task.evidence.map((item) => (
                <span key={item}>
                  <Code />
                  {item}
                </span>
              ))}
            </div>
          </Panel>

          <Panel title="Execution Timeline">
            <ol className="timeline-list">
              {reviewChecklist.map((item, index) => (
                <li key={item}>
                  <span>{String(index + 1).padStart(2, "0")}</span>
                  <strong>{item}</strong>
                </li>
              ))}
            </ol>
          </Panel>
        </div>

        <aside className="task-sidebar">
          <Panel title="Stage & Quality">
            <Metric label="目前階段" value={task.step} />
            <Metric label="品質狀態" value={`品質 ${task.score} / 10`} />
            <Metric label="下一步" value={nextAction} />
            <Metric label="來源" value={task.source} />
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

          <Panel title="Risk Notes">
            <ul className="clean-list">
              <li>
                {isReview
                  ? "Approve 後才能進入 WRAP；reject 後回到 Chat 繼續探索或規劃。"
                  : "尚未進入人工審查，先補齊 Definition 或執行證據。"}
              </li>
              <li>Follow-up Task 只建立提案，不會自動開工。</li>
            </ul>
          </Panel>

          <Panel title="Linked Work">
            <div className="linked-work">
              <GitPullRequest aria-hidden="true" />
              <span>{task.id} · {task.skill}</span>
            </div>
            {task.gaps.length > 0 && (
              <div className="linked-work warning">
                <WarningCircle aria-hidden="true" />
                <span>{task.gaps.length} 個缺口待補</span>
              </div>
            )}
          </Panel>
        </aside>
      </div>
    </section>
  );
}
