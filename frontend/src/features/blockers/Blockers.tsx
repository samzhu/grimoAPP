import type { Task } from "../../domain/task/task-types";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

export function Blockers({ tasks }: { tasks: Task[] }) {
  const blocked = tasks.filter((task) => task.state === "BLOCKED");
  return (
    <section className="split-page">
      <div className="section-head">
        <div>
          <h1>待處理</h1>
          <p>只整理需要人介入的例外事項，主流程仍留在 Task 管理看板。</p>
        </div>
      </div>
      <div className="stack-list">
        {blocked.map((task) => (
          <Panel key={task.id} title={task.title}>
            <Metric label="任務" value={task.id} />
            <Metric label="原因" value={task.gaps.join(" / ")} />
            <Metric label="建議" value="補權限、補 context 或回到 Chat 釐清限制" />
          </Panel>
        ))}
      </div>
    </section>
  );
}

