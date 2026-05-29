import type { ReactNode } from "react";

export type BadgeTone = "neutral" | "good" | "warn" | "bad" | "info";
export type BadgeKind = "default" | "task-id" | "state" | "label" | "metric";

export function Badge({
  children,
  tone = "neutral",
  kind = "default",
}: {
  children: ReactNode;
  tone?: BadgeTone;
  kind?: BadgeKind;
}) {
  return <span className={`badge ${tone} ${kind}`}>{children}</span>;
}
