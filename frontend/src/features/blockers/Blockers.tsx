import {
  ArrowUUpLeft,
  ChatCircleText,
  ClockCounterClockwise,
} from "@phosphor-icons/react";
import type { Task } from "../../domain/task/task-types";
import { stateTone } from "../../domain/task/task-selectors";
import { Badge } from "../../shared/ui/Badge";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

function actionLabel() {
  return "回到 Chat 繼續探索或規劃";
}

export function Blockers({
  tasks,
  onOpenChat,
}: {
  tasks: Task[];
  onOpenChat: (taskId: string) => void;
}) {
  const review = tasks.filter((task) => task.state === "REVIEW");
  const repairTasks = tasks.filter(
    (task) => (task.state === "READY" || task.state === "RUNNING") && task.gaps.length > 0,
  );
  const definitionGaps = tasks.filter(
    (task) => (task.state === "BACKLOG" || task.state === "DEFINING") && task.gaps.length > 0,
  );
  const attentionTasks = [...review, ...repairTasks];

  return (
    <section className="attention-page">
      <div className="section-head">
        <div>
          <h1>待處理</h1>
          <p>集中人工審查、阻塞條件與需要繼續討論的任務，處理完再回到 Task 管理看板。</p>
        </div>
      </div>

      <div className="attention-summary">
        <div>
          <span>人工審查</span>
          <strong>{review.length}</strong>
        </div>
        <div>
          <span>修復項</span>
          <strong>{repairTasks.length}</strong>
        </div>
        <div>
          <span>待補定義</span>
          <strong>{definitionGaps.length}</strong>
        </div>
      </div>

      <div className="attention-layout">
        <div className="attention-main">
          <section className="attention-queue" aria-labelledby="attention-queue-title">
            <div className="attention-section-head">
              <div>
                <h2 id="attention-queue-title">優先處理</h2>
                <p>先處理 REVIEW 和 NEEDS_HUMAN 修復項；它們會卡住 release 或 dispatcher。</p>
              </div>
              <Badge tone={attentionTasks.length > 0 ? "warn" : "good"}>
                {attentionTasks.length} 個
              </Badge>
            </div>

            {attentionTasks.map((task) => (
              <article className="attention-task" key={task.id}>
                <div className="attention-task-head">
                  <div className="badge-row">
                    <Badge kind="task-id">{task.id}</Badge>
                    <Badge kind="state" tone={stateTone(task.state)}>
                      {task.state}
                    </Badge>
                    {task.labels.slice(0, 2).map((label) => (
                      <Badge key={label} kind="label">
                        {label}
                      </Badge>
                    ))}
                  </div>
                  <strong>{task.score > 0 ? task.score.toFixed(1) : "未評分"}</strong>
                </div>

                <div className="attention-task-body">
                  <h3>{task.title}</h3>
                  <p>{task.description}</p>
                </div>

                <div className="attention-task-grid">
                  <div>
                    <span>下一步</span>
                    <strong>{actionLabel()}</strong>
                  </div>
                  <div>
                    <span>缺口</span>
                    <strong>{task.gaps[0] ?? "無阻塞缺口"}</strong>
                  </div>
                  <div>
                    <span>更新</span>
                    <strong>{task.updatedAt}</strong>
                  </div>
                </div>

                <div className="attention-task-actions">
                  <button
                    type="button"
                    className="primary-button"
                    onClick={() => onOpenChat(task.id)}
                  >
                    <ChatCircleText aria-hidden="true" />
                    Chat
                  </button>
                </div>
              </article>
            ))}
          </section>
        </div>

        <aside className="attention-sidebar">
          <Panel title="待補定義">
            <div className="attention-mini-list">
              {definitionGaps.map((task) => (
                <div className="attention-mini-item" key={task.id}>
                  <div>
                    <Badge kind="task-id">{task.id}</Badge>
                    <strong>{task.title}</strong>
                  </div>
                  <span>{task.gaps.join(" / ")}</span>
                </div>
              ))}
            </div>
          </Panel>

          <Panel title="阻塞摘要">
            <Metric label="最高優先" value={attentionTasks[0]?.id ?? "無"} />
            <Metric label="最近更新" value={attentionTasks[0]?.updatedAt ?? "無"} />
            <Metric label="修復路徑" value="補權限、回到 Chat 或人工核准" />
          </Panel>

          <Panel title="處理紀錄">
            <div className="attention-log">
              <span>
                <ClockCounterClockwise aria-hidden="true" />
                GRM-188 等待人工審查
              </span>
              <span>
                <ArrowUUpLeft aria-hidden="true" />
                GRM-176 需補 repo permission probe
              </span>
            </div>
          </Panel>
        </aside>
      </div>
    </section>
  );
}
