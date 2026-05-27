import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

export function Projects() {
  return (
    <section className="projects-view">
      <div className="section-head">
        <div>
          <h1>專案管理</h1>
          <p>管理本機 repo / codebase，讓任務、執行紀錄與審查資料有明確歸屬。</p>
        </div>
      </div>
      <div className="project-grid">
        <Panel title="grimo/frontend">
          <Metric label="資料夾" value="/frontend" />
          <Metric label="工作流" value="開發工作流" />
          <Metric label="狀態" value="POC 建立中" />
        </Panel>
        <Panel title="新增專案">
          <div className="form-stack">
            <label>
              名稱
              <input placeholder="例如 grimo/web" />
            </label>
            <label>
              專案資料夾
              <input placeholder="/Users/samzhu/workspace/grimo" />
            </label>
            <button type="button" className="primary-button">
              新增專案
            </button>
          </div>
        </Panel>
      </div>
    </section>
  );
}

