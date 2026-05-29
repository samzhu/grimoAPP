// Source: docs/grimo/ui/prototype/index.html taskLabelPicker.
export const taskLabelOptions = [
  "bug",
  "documentation",
  "duplicate",
  "enhancement",
  "good first issue",
  "help wanted",
  "invalid",
  "question",
  "wontfix",
  "frontend",
  "backend",
  "ci/cd",
  "design",
  "research",
] as const;

export type TaskLabel = (typeof taskLabelOptions)[number];
