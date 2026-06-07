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
  isPinned,
  onSelect,
  onClose,
  onTogglePin,
}: {
  active: View;
  isPinned: boolean;
  onSelect: (view: View) => void;
  onClose: () => void;
  onTogglePin: () => void;
}) {
  const items: Array<[View, string, ReactNode]> = [
    ["tasks", "Task 管理", <Rows key="tasks" />],
    ["blockers", "待處理", <WarningCircle key="blockers" />],
    ["projects", "專案", <Folder key="projects" />],
    ["chat", "Chat", <ChatCenteredText key="chat" />],
    ["workflow", "Workflow", <GitBranch key="workflow" />],
  ];

  return (
    <nav className="side-navigation" aria-label="主要頁面">
      <div className="side-navigation-controls">
        <button className="side-navigation-control" type="button" onClick={onClose}>
          <span>主選單</span>
          <CaretLeft />
        </button>
        <button
          className={isPinned ? "side-navigation-pin active" : "side-navigation-pin"}
          type="button"
          aria-label={isPinned ? "取消固定主選單" : "固定主選單"}
          aria-pressed={isPinned}
          onClick={onTogglePin}
        >
          <PushPinSimple weight={isPinned ? "fill" : "regular"} />
        </button>
      </div>
      {items.map(([key, label, icon]) => (
        <button
          key={key}
          className={active === key ? "side-navigation-item active" : "side-navigation-item"}
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
