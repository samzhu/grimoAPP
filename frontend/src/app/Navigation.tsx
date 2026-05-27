import type { ReactNode } from "react";
import {
  CaretLeft,
  ChatCenteredText,
  Folder,
  GitBranch,
  PushPinSimple,
  Rows,
  WarningCircle,
} from "@phosphor-icons/react";
import type { View } from "./view-types";

export function Navigation({
  active,
  onSelect,
  onClose,
}: {
  active: View;
  onSelect: (view: View) => void;
  onClose: () => void;
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
      <div className="rail-controls">
        <button className="rail-control" type="button" onClick={onClose}>
          <span>主選單</span>
          <CaretLeft />
        </button>
        <button className="rail-pin" type="button" aria-label="固定主選單">
          <PushPinSimple />
        </button>
      </div>
      <button className="rail-item active rail-section" type="button">
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

