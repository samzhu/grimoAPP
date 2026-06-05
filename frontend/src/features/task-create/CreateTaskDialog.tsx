import { X } from "@phosphor-icons/react";
import { useState } from "react";
import type { FormEvent } from "react";
import { taskLabelOptions } from "../../domain/task/task-labels";
import type { CreateTaskInput } from "../task-board/task-api";

export function CreateTaskDialog({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (input: CreateTaskInput) => Promise<void>;
}) {
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submitTask = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setError("");
    setIsSubmitting(true);
    try {
      await onSubmit({
        title: String(form.get("title") ?? "").trim(),
        body: String(form.get("body") ?? "").trim(),
        labels: parseLabels(String(form.get("labels") ?? "")),
      });
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "建立 Task 失敗");
    } finally {
      setIsSubmitting(false);
    }
  };

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
            <p>先把任務放進 BACKLOG；第一次 Chat 才進入 workflow。</p>
          </div>
          <button className="icon-button" type="button" aria-label="關閉新增 Task" onClick={onClose}>
            <X />
          </button>
        </div>
        <form
          className="task-workspace-body task-create-form"
          onSubmit={submitTask}
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
              </div>
            </aside>
          </div>
          {error && <p className="form-message error">{error}</p>}
          <div className="task-workspace-actions">
            <button className="primary-button" type="submit" disabled={isSubmitting}>
              {isSubmitting ? "建立中..." : "建立 Task"}
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

function parseLabels(value: string) {
  return [...new Set(value
    .split(",")
    .map((label) => label.trim())
    .filter(Boolean))];
}
