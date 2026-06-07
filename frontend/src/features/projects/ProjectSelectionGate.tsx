import {
  ArrowRight,
  ChatCircleText,
  GitBranch,
  ListChecks,
  Sparkle,
} from "@phosphor-icons/react";

type ProjectSelectionGateReason = "first-run" | "missing-session" | "no-context";

type ProjectSelectionGateProps = {
  reason: ProjectSelectionGateReason;
  message?: string;
  onCreateProject: () => void;
  onRetry?: () => void;
};

const setupSteps = [
  {
    icon: GitBranch,
    title: "選擇本機 repo",
    copy: "讓 Task、對話和審查證據都能回到同一個 codebase。",
  },
  {
    icon: ListChecks,
    title: "套用 Project workflow",
    copy: "建立後直接用 Project 的角色、步驟和品質門檻管理工作。",
  },
  {
    icon: ChatCircleText,
    title: "開始形成 Task",
    copy: "Chat 會在 Project context 裡沉澱成可追蹤的 Task。",
  },
];

const setupSuggestions = [
  "用目前 repo 建立 Project",
  "先建立空白 Project",
  "我想把現有任務整理進來",
];

type ProjectSetupGuideProps = {
  reason: ProjectSelectionGateReason;
  message?: string;
  onCreateProject: () => void;
};

function ProjectSetupGuide({
  reason,
  message,
  onCreateProject,
}: ProjectSetupGuideProps) {
  const eyebrow = reason === "first-run" ? "新手引導" : "尚未開啟 Project";
  return (
    <div className="project-setup-copilot">
      <div className="project-setup-copy">
        <p className="project-setup-eyebrow">{eyebrow}</p>
        <h1 id="project-selection-title">建立 Project 工作台</h1>
        <p>
          {message ||
            "把一個本機 repo 變成 Grimo Project，之後 Task、Chat、執行紀錄和審查證據都會歸在同一個工作台。"}
        </p>
        <button className="primary-button" type="button" onClick={onCreateProject}>
          <span>建立新 Project</span>
          <ArrowRight aria-hidden="true" />
        </button>
      </div>

      <div className="project-setup-thread" aria-label="Project setup copilot preview">
        <div className="setup-thread-header">
          <div className="setup-thread-icon" aria-hidden="true">
            <Sparkle />
          </div>
          <div>
            <strong>Project Setup Copilot</strong>
            <span>建立前先把 repo、workflow 和第一批 Task context 對齊。</span>
          </div>
        </div>

        <div className="setup-thread-message assistant">
          <strong>我會協助你建立第一個 Project。</strong>
          <p>先選 repo，再確認工作流；建立後會直接進入 Task 工作台。</p>
        </div>

        <div className="setup-suggestion-list" aria-label="Project setup suggestions">
          {setupSuggestions.map((suggestion) => (
            <button className="setup-suggestion" key={suggestion} type="button" onClick={onCreateProject}>
              {suggestion}
            </button>
          ))}
        </div>

        <div className="setup-thread-composer">
          <span>描述這個 repo 或你想先追蹤的工作</span>
          <button type="button" onClick={onCreateProject} aria-label="開始建立 Project">
            <ArrowRight aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className="project-setup-steps" aria-label="建立 Project 後會啟用的工作台能力">
        {setupSteps.map((step) => {
          const Icon = step.icon;
          return (
            <div className="project-setup-step" key={step.title}>
              <Icon aria-hidden="true" />
              <div>
                <strong>{step.title}</strong>
                <p>{step.copy}</p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

type ProjectSetupErrorProps = {
  message?: string;
  onRetry: () => void;
};

function ProjectSetupError({ message, onRetry }: ProjectSetupErrorProps) {
  return (
    <div className="project-setup-error">
      <div>
        <h1 id="project-selection-title">無法載入 Project context</h1>
        <p>{message || "Project 載入失敗，請重試。"}</p>
      </div>
      <button className="primary-button" type="button" onClick={onRetry}>
        重試
      </button>
    </div>
  );
}

export function ProjectSelectionGate({
  reason,
  message,
  onCreateProject,
  onRetry,
}: ProjectSelectionGateProps) {
  return (
    <section className="project-selection-gate" aria-labelledby="project-selection-title">
      <div className="project-selection-inner">
        {onRetry ? (
          <ProjectSetupError message={message} onRetry={onRetry} />
        ) : (
          <ProjectSetupGuide
            reason={reason}
            message={message}
            onCreateProject={onCreateProject}
          />
        )}
      </div>
    </section>
  );
}
