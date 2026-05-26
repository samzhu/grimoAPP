import { type ReactNode, useMemo, useState } from "react";
import {
  AuiIf,
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  type ChatModelAdapter,
  useLocalRuntime,
} from "@assistant-ui/react";
import {
  ArrowsClockwise,
  BellSimple,
  CheckCircle,
  ChatCenteredText,
  Code,
  Folder,
  GitBranch,
  MagnifyingGlass,
  PaperPlaneTilt,
  Play,
  PushPinSimple,
  Rows,
  WarningCircle,
} from "@phosphor-icons/react";

type TaskState =
  | "BACKLOG"
  | "DEFINING"
  | "READY"
  | "RUNNING"
  | "REVIEW"
  | "DONE"
  | "BLOCKED";

type Task = {
  id: string;
  title: string;
  state: TaskState;
  source: string;
  skill: string;
  score: number;
  step: string;
  updatedAt: string;
  description: string;
  acceptance: string[];
  gaps: string[];
  evidence: string[];
};

type View = "tasks" | "blockers" | "projects" | "chat" | "workflow";

const tasks: Task[] = [
  {
    id: "GRM-219",
    title: "Voice composer 與審查附件呈現重構",
    state: "DEFINING",
    source: "chat",
    skill: "frontend-ui",
    score: 7.3,
    step: "Prototype",
    updatedAt: "2h ago",
    description:
      "Voice 應回到 Chat composer，Task detail 需要能展開 code evidence 與 screenshot evidence。",
    acceptance: [
      "Voice toggle 在 composer 內顯示狀態",
      "Task detail 能展開 code evidence",
      "審查附件保留測試與截圖來源",
    ],
    gaps: ["補 voice state 邊界", "確認 screenshot evidence policy"],
    evidence: ["task-detail.tsx", "checkpoint-42", "Linear GRM-184"],
  },
  {
    id: "GRM-207",
    title: "External issue 匯入 Task List State abstraction",
    state: "RUNNING",
    source: "github_issue",
    skill: "workflow-modeling",
    score: 8.8,
    step: "Dev",
    updatedAt: "45m ago",
    description:
      "外部 issue、CLI request、chat intent 需要進入同一套 Task List State。",
    acceptance: [
      "GitHub / Linear / Chat 來源都能形成 Task",
      "原始來源保留在 context",
      "Task state 不受來源類型影響",
    ],
    gaps: ["Jira field mapping 需要 fallback"],
    evidence: ["GitHub fixture", "Linear webhook sample"],
  },
  {
    id: "GRM-201",
    title: "執行前設定與本機能力檢查",
    state: "READY",
    source: "codex",
    skill: "runtime-planning",
    score: 9.1,
    step: "Ready boundary",
    updatedAt: "1h ago",
    description:
      "READY 任務開始前檢查 repo binding、tool availability 與 risk level。",
    acceptance: [
      "缺工具或權限時轉為 BLOCKED",
      "本機能力由系統在執行前檢查",
    ],
    gaps: [],
    evidence: ["routing policy draft", "capability probe"],
  },
  {
    id: "GRM-188",
    title: "Task detail 顯示審查附件與人工核准",
    state: "REVIEW",
    source: "codex",
    skill: "review-materials",
    score: 9.4,
    step: "Review",
    updatedAt: "12m ago",
    description:
      "Task detail 把執行結果、測試、截圖與風險整理成可審查附件。",
    acceptance: [
      "REVIEW 狀態才顯示 approve / reject",
      "審查附件包含測試、截圖與風險",
      "退回時可回到 Chat 補上下文",
    ],
    gaps: [],
    evidence: ["Playwright screenshot", "typecheck", "risk note"],
  },
  {
    id: "GRM-176",
    title: "本機 daemon repo binding 與多專案 session index",
    state: "BLOCKED",
    source: "codex",
    skill: "local-env",
    score: 6.6,
    step: "Prototype",
    updatedAt: "3h ago",
    description:
      "本機 runner 需要以專案資料夾做 repo binding，不能假設全域 session 可用。",
    acceptance: ["權限 probe 失敗時任務進入 BLOCKED", "修復後可回到原流程"],
    gaps: ["repo permission probe 失敗", "需要確認 monorepo 權限與路徑"],
    evidence: ["local runner log", "repo probe result"],
  },
  {
    id: "GRM-160",
    title: "Board-facing states 對齊 BACKLOG 到 BLOCKED",
    state: "DONE",
    source: "manual",
    skill: "workflow-modeling",
    score: 9.8,
    step: "Wrap",
    updatedAt: "yesterday",
    description: "Board 只保留 Task List State，recipe step 只在 detail 呈現。",
    acceptance: [
      "Board 顯示 BACKLOG 到 DONE",
      "BLOCKED 留在待處理視圖",
      "recipe step 只在 detail 或 Workflow 呈現",
    ],
    gaps: [],
    evidence: ["domain model test", "migration note"],
  },
];

const stateColumns: TaskState[] = [
  "BACKLOG",
  "DEFINING",
  "READY",
  "RUNNING",
  "REVIEW",
  "DONE",
];

const workflowRows = [
  ["Discuss / Explore / Prototype / Spec / Usage / Tkt", "DEFINING", "Definition Package"],
  ["Ready boundary", "READY", "Assignment + runtime preflight"],
  ["Dev", "RUNNING", "Execution evidence complete"],
  ["Review", "REVIEW", "Human approve / reject"],
  ["Wrap", "DONE", "Delivery summary + learning proposal"],
  ["Any stop condition", "BLOCKED", "補依賴、權限或 context"],
];

const modelAdapter: ChatModelAdapter = {
  async run() {
    return {
      content: [
        {
          type: "text",
          text:
            "POC 回覆：這裡使用 assistant-ui 的 ThreadPrimitive 與 ComposerPrimitive。之後 Spring Boot 只要提供 chat endpoint，即可替換這個本地 adapter。",
        },
      ],
    };
  },
};

function RuntimeProvider({ children }: { children: ReactNode }) {
  const runtime = useLocalRuntime(modelAdapter);
  return (
    <AssistantRuntimeProvider runtime={runtime}>
      {children}
    </AssistantRuntimeProvider>
  );
}

export function App() {
  const [view, setView] = useState<View>("tasks");
  const [selectedTaskId, setSelectedTaskId] = useState(tasks[0].id);
  const [query, setQuery] = useState("");

  const filteredTasks = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return tasks;
    return tasks.filter((task) =>
      [task.id, task.title, task.state, task.source, task.skill]
        .join(" ")
        .toLowerCase()
        .includes(normalized),
    );
  }, [query]);

  const selectedTask =
    tasks.find((task) => task.id === selectedTaskId) ?? tasks[0];

  return (
    <RuntimeProvider>
      <div className="app-shell">
        <header className="topbar">
          <div className="brand-mark">G</div>
          <div className="brand-copy">
            <strong>Grimo</strong>
            <span>AI Development Workbench POC</span>
          </div>
          <div className="project-context">
            <span>目前專案</span>
            <strong>grimo/frontend</strong>
            <code>/Users/samzhu/workspace/github-samzhu/grimoAPP/frontend</code>
          </div>
        </header>

        <div className="workspace-shell">
          <Navigation active={view} onSelect={setView} />
          <main className="main-surface">
            {view === "tasks" && (
              <TaskWorkbench
                filteredTasks={filteredTasks}
                query={query}
                selectedTask={selectedTask}
                onQueryChange={setQuery}
                onSelectTask={setSelectedTaskId}
              />
            )}
            {view === "blockers" && <Blockers />}
            {view === "projects" && <Projects />}
            {view === "chat" && <AssistantChat selectedTask={selectedTask} />}
            {view === "workflow" && <Workflow />}
          </main>
        </div>
      </div>
    </RuntimeProvider>
  );
}

function Navigation({
  active,
  onSelect,
}: {
  active: View;
  onSelect: (view: View) => void;
}) {
  const items: Array<[View, string, ReactNode]> = [
    ["tasks", "Task 管理", <Rows key="tasks" />],
    ["blockers", "待處理", <WarningCircle key="blockers" />],
    ["projects", "專案", <Folder key="projects" />],
    ["chat", "Chat", <ChatCenteredText key="chat" />],
    ["workflow", "Workflow", <GitBranch key="workflow" />],
  ];

  return (
    <nav className="rail" aria-label="主要頁面">
      <button className="rail-control" type="button">
        <PushPinSimple />
        <span>固定</span>
      </button>
      {items.map(([key, label, icon]) => (
        <button
          key={key}
          className={active === key ? "rail-item active" : "rail-item"}
          type="button"
          onClick={() => onSelect(key)}
        >
          {icon}
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}

function TaskWorkbench({
  filteredTasks,
  query,
  selectedTask,
  onQueryChange,
  onSelectTask,
}: {
  filteredTasks: Task[];
  query: string;
  selectedTask: Task;
  onQueryChange: (query: string) => void;
  onSelectTask: (taskId: string) => void;
}) {
  return (
    <section className="task-workbench">
      <div className="board-pane">
        <div className="section-head">
          <div>
            <h1>任務工作台</h1>
            <p>從 Chat、CLI 或 issue 進來的工作先成為任務，再由 Grimo 管理定義、執行與審查。</p>
          </div>
          <label className="search-field">
            <MagnifyingGlass />
            <input
              value={query}
              onChange={(event) => onQueryChange(event.target.value)}
              placeholder="搜尋任務 / 來源 / skill"
            />
          </label>
        </div>

        <div className="board-grid" aria-label="Task board">
          {stateColumns.map((state) => {
            const columnTasks = filteredTasks.filter((task) => task.state === state);
            return (
              <section className="board-column" key={state}>
                <div className="column-head">
                  <strong>{state}</strong>
                  <span>{columnTasks.length}</span>
                </div>
                <div className="column-body">
                  {columnTasks.map((task) => (
                    <button
                      className={
                        selectedTask.id === task.id ? "task-card selected" : "task-card"
                      }
                      key={task.id}
                      type="button"
                      onClick={() => onSelectTask(task.id)}
                    >
                      <span className="task-id">{task.id}</span>
                      <strong>{task.title}</strong>
                      <span>{task.updatedAt}</span>
                      <div className="task-card-foot">
                        <Badge>{task.source}</Badge>
                        <Badge tone={qualityTone(task.score)}>品質 {task.score}</Badge>
                      </div>
                    </button>
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      </div>

      <TaskDetail task={selectedTask} />
    </section>
  );
}

function TaskDetail({ task }: { task: Task }) {
  return (
    <aside className="detail-pane">
      <div className="detail-head">
        <div className="badge-row">
          <Badge>{task.id}</Badge>
          <Badge tone={stateTone(task.state)}>{task.state}</Badge>
          <Badge>{task.step}</Badge>
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
              : "回到 Chat 補上下文或查看完整頁"}
          </span>
        </div>
        <button type="button" className="primary-button">
          {task.state === "READY" ? <Play /> : <ChatCenteredText />}
          {task.state === "READY" ? "開始執行" : "使用 Chat"}
        </button>
      </div>
    </aside>
  );
}

function AssistantChat({ selectedTask }: { selectedTask: Task }) {
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
                <Badge>{selectedTask.id}</Badge>
                <span>{selectedTask.title}</span>
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
          <Metric label="任務" value={`${selectedTask.id} · ${selectedTask.state}`} />
          <Metric label="Skill" value={selectedTask.skill} />
          <Metric label="品質" value={`${selectedTask.score} / 10`} />
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

function Blockers() {
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

function Projects() {
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

function Workflow() {
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
                <Badge tone={stateTone(state as TaskState)}>{state}</Badge>
                <span>{gate}</span>
              </div>
            ))}
          </div>
        </Panel>
        <Panel title="Quality Loop">
          <div className="loop-list">
            {["審查", "評分", "修正", "quality_score > 9"].map((item, index) => (
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

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="panel">
      <h3>{title}</h3>
      <div className="panel-body">{children}</div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: "neutral" | "good" | "warn" | "bad" | "info";
}) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

function qualityTone(score: number) {
  if (score >= 9) return "good";
  if (score >= 8) return "warn";
  if (score > 0) return "info";
  return "neutral";
}

function stateTone(state: TaskState) {
  if (state === "DONE" || state === "READY") return "good";
  if (state === "BLOCKED") return "bad";
  if (state === "REVIEW" || state === "RUNNING") return "warn";
  return "neutral";
}
