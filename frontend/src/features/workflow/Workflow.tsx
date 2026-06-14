import { workflowRows } from "../../domain/workflow/workflow-fixtures";
import type { TaskState } from "../../domain/task/task-types";
import { stateTone } from "../../domain/task/task-selectors";
import { Badge } from "../../shared/ui/Badge";
import { Panel } from "../../shared/ui/Panel";

export function Workflow() {
  return (
    <section className="workflow-view">
      <div className="section-head">
        <div>
          <h1>Workflow 設計</h1>
          <p>設計任務如何從定義、執行、審查到收尾，並設定哪些邊界需要人工確認。</p>
        </div>
      </div>
      <div className="workflow-grid">
        <Panel title="Recipe State Mapping">
          <div className="mapping-table">
            {workflowRows.map(([step, state, gate]) => (
              <div className="mapping-row" key={step}>
                <strong>{step}</strong>
                <Badge kind="state" tone={stateTone(state as TaskState)}>
                  {state}
                </Badge>
                <span>{gate}</span>
              </div>
            ))}
          </div>
        </Panel>
        <Panel title="Quality Loop">
          <div className="loop-list">
            {["Review", "Rating", "Gate", "Fix"].map((item, index) => (
              <div className="loop-item" key={item}>
                <span>{String(index + 1).padStart(2, "0")}</span>
                <strong>{item}</strong>
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </section>
  );
}
