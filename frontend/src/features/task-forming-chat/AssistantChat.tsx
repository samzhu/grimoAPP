import {
  AuiIf,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
} from "@assistant-ui/react";
import { PaperPlaneTilt } from "@phosphor-icons/react";
import type { Task } from "../../domain/task/task-types";
import { Badge } from "../../shared/ui/Badge";
import { Metric } from "../../shared/ui/Metric";
import { Panel } from "../../shared/ui/Panel";

export function AssistantChat({ selectedTask }: { selectedTask: Task | null }) {
  return (
    <section className="chat-view">
      <div className="chat-main">
        <div className="section-head">
          <div>
            <h1>工作形成 Chat</h1>
            <p>assistant-ui primitives 嵌入 Grimo 版面，用於驗證 composer、thread 與 message 呈現。</p>
          </div>
        </div>

        <ThreadPrimitive.Root className="assistant-thread">
          <ThreadPrimitive.Viewport className="thread-viewport" autoScroll>
            <AuiIf condition={(state) => state.thread.isEmpty}>
              <div className="thread-empty">
                <Badge>assistant-ui</Badge>
                <h2>把討論轉成 Grimo Task</h2>
                <p>
                  目前接的是本地 POC adapter。之後 Spring Boot 提供 API 後，可替換 adapter，不需要重寫 UI。
                </p>
              </div>
            </AuiIf>

            <ThreadPrimitive.Messages>
              {({ message }) => (
                <MessagePrimitive.Root className={`message-bubble ${message.role}`}>
                  <div className="message-role">{message.role}</div>
                  <MessagePrimitive.Content />
                </MessagePrimitive.Root>
              )}
            </ThreadPrimitive.Messages>

            <ThreadPrimitive.ViewportFooter className="thread-footer">
              <div className="context-strip">
                {selectedTask ? (
                  <>
                    <Badge>{selectedTask.id}</Badge>
                    <span>{selectedTask.title}</span>
                  </>
                ) : (
                  <>
                    <Badge>未連結</Badge>
                    <span>可從空白討論形成新 Task</span>
                  </>
                )}
              </div>
              <ComposerPrimitive.Root className="assistant-composer">
                <ComposerPrimitive.Input
                  className="composer-input"
                  placeholder="描述要形成的工作、限制或驗收條件"
                  rows={2}
                  submitMode="ctrlEnter"
                />
                <div className="composer-actions">
                  <ComposerPrimitive.Send className="send-button" aria-label="送出訊息">
                    <PaperPlaneTilt />
                  </ComposerPrimitive.Send>
                </div>
              </ComposerPrimitive.Root>
            </ThreadPrimitive.ViewportFooter>
          </ThreadPrimitive.Viewport>
        </ThreadPrimitive.Root>
      </div>

      <aside className="chat-side">
        <Panel title="目前連結 Task">
          {selectedTask ? (
            <>
              <Metric label="任務" value={`${selectedTask.id} · ${selectedTask.state}`} />
              <Metric label="Skill" value={selectedTask.skill} />
              <Metric label="品質" value={`${selectedTask.score} / 10`} />
            </>
          ) : (
            <>
              <Metric label="任務" value="尚未連結" />
              <Metric label="入口" value="Discuss / Task forming" />
              <Metric label="下一步" value="補足背景後建立 Task" />
            </>
          )}
        </Panel>
        <Panel title="Runtime 策略">
          <p className="panel-copy">
            POC 使用 assistant-ui LocalRuntime 與自訂 model adapter。正式版改接 Spring Boot REST/SSE 後端。
          </p>
        </Panel>
      </aside>
    </section>
  );
}
