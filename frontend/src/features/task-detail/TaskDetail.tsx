import {
  ChatCenteredText,
  Code,
  Play,
  PushPinSimple,
  X,
} from "@phosphor-icons/react";
import type { Task } from "../../domain/task/task-types";
import { stateTone } from "../../domain/task/task-selectors";
import { Badge } from "../../shared/ui/Badge";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

export function TaskDetail({
  task,
  isPinned,
  onClose,
  onOpenFullPage,
  onTogglePin,
  onOpenChat,
}: {
  task: Task;
  isPinned: boolean;
  onClose: () => void;
  onOpenFullPage: () => void;
  onTogglePin: () => void;
  onOpenChat: () => void;
}) {
  return (
    <aside className="task-details-pane">
      <div className="detail-toolbar">
        <h2>任務詳情</h2>
        <div className="detail-actions">
          <button className="primary-button" type="button" onClick={onOpenFullPage}>在完整頁開啟</button>
          <button
            className="icon-button"
            type="button"
            aria-label={isPinned ? "取消固定任務詳情" : "固定任務詳情"}
            aria-pressed={isPinned}
            onClick={onTogglePin}
          >
            <PushPinSimple />
          </button>
          <button className="icon-button" type="button" aria-label="關閉任務詳情" onClick={onClose}>
            <X />
          </button>
        </div>
      </div>
      <div className="detail-head">
        <div className="badge-row">
          <Badge kind="task-id">{task.id}</Badge>
          <Badge kind="state" tone={stateTone(task.state)}>
            {task.state}
          </Badge>
        </div>
        <h2>{task.title}</h2>
        <p>{task.description}</p>
      </div>

      <Panel title="Stage & Quality">
        <Metric label="目前階段" value={task.step} />
        <Metric label="品質門檻" value={`${task.score} / 10`} />
        <Metric
          label="狀態"
          value={task.score >= 9 ? "已達通過門檻" : "需要補齊證據"}
        />
        <Metric label="來源" value={task.source} />
      </Panel>

      <Panel title={task.state === "REVIEW" ? "Review Materials" : "Acceptance Gate"}>
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

      {task.gaps.length > 0 && (
        <Panel title="待補缺口">
          <ul className="clean-list">
            {task.gaps.map((gap) => (
              <li key={gap}>{gap}</li>
            ))}
          </ul>
        </Panel>
      )}

      <div className="action-band">
        <div>
          <strong>{task.state === "REVIEW" ? "等待人工審查" : "下一步"}</strong>
          <span>
            {task.state === "READY"
              ? "開始前執行本機能力檢查"
              : "回到 Chat 繼續探索、規劃或查看完整頁"}
          </span>
        </div>
        <button
          type="button"
          className="primary-button"
          onClick={task.state === "READY" ? undefined : onOpenChat}
        >
          {task.state === "READY" ? <Play /> : <ChatCenteredText />}
          {task.state === "READY" ? "開始執行" : "Chat"}
        </button>
      </div>
    </aside>
  );
}
