import type { Task, TaskState } from "./task-types";

export type TaskTone = "neutral" | "good" | "warn" | "bad" | "info";

export function taskMatchesQuery(task: Task, query: string) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return true;

  return [task.id, task.title, task.state, task.skill]
    .join(" ")
    .toLowerCase()
    .includes(normalized);
}

export function stateTone(state: TaskState): TaskTone {
  if (state === "DONE" || state === "READY") return "good";
  if (state === "BLOCKED") return "bad";
  if (state === "REVIEW" || state === "RUNNING") return "warn";
  return "neutral";
}
