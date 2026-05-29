import { X } from "@phosphor-icons/react";
import { taskLabelOptions } from "../../domain/task/task-labels";

export function CreateTaskDialog({ onClose }: { onClose: () => void }) {
  return (
    <div className="task-workspace-modal" role="presentation">
      <section
        className="task-workspace-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-task-title"
      >
        <div className="task-workspace-head">
          <div>
            <h2 id="create-task-title">新增 Task</h2>
            <p>先建立可進入 DEFINING 的工作草稿；後續再補 acceptance 與缺口。</p>
          </div>
          <button className="icon-button" type="button" aria-label="關閉新增 Task" onClick={onClose}>
            <X />
          </button>
        </div>
        <form
          className="task-workspace-body task-create-form"
          onSubmit={(event) => {
            event.preventDefault();
            onClose();
          }}
        >
          <div className="task-workspace-grid">
            <div className="task-workspace-stack">
              <div className="form-field">
                <label htmlFor="task-title">標題</label>
                <input
                  className="text-control"
                  id="task-title"
                  name="title"
                  placeholder="例如 Task detail 顯示審查附件與人工核准"
                  required
                />
              </div>
              <div className="form-field">
                <label htmlFor="task-body">任務內容</label>
                <textarea
                  className="text-control task-body-input"
                  id="task-body"
                  name="body"
                  placeholder="描述目標、限制、成功條件或目前缺口"
                />
              </div>
            </div>
            <aside className="panel">
              <h3>Definition hints</h3>
              <div className="panel-body">
                <div className="form-field">
                  <label htmlFor="task-labels">Labels</label>
                  <input
                    className="text-control"
                    id="task-labels"
                    name="labels"
                    list="task-label-options"
                    placeholder="frontend, enhancement"
                  />
                  <datalist id="task-label-options">
                    {taskLabelOptions.map((label) => (
                      <option key={label} value={label} />
                    ))}
                  </datalist>
                </div>
                <div className="form-field">
                  <label htmlFor="task-skill">建議 skill</label>
                  <select className="text-control" id="task-skill" name="skill">
                    <option>frontend-ui</option>
                    <option>workflow-modeling</option>
                    <option>runtime-planning</option>
                  </select>
                </div>
              </div>
            </aside>
          </div>
          <div className="task-workspace-actions">
            <button className="primary-button" type="submit">
              建立草稿
            </button>
            <button className="icon-text-button" type="button" onClick={onClose}>
              取消
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
