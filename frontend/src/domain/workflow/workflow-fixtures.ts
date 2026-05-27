export const workflowRows = [
  ["Discuss / Explore / Prototype / Spec / Usage / Tkt", "DEFINING", "Definition Package"],
  ["Ready boundary", "READY", "Assignment + runtime preflight"],
  ["Dev", "RUNNING", "Execution evidence complete"],
  ["Review", "REVIEW", "Human approve / reject"],
  ["Wrap", "DONE", "Delivery summary + learning proposal"],
  ["Any stop condition", "BLOCKED", "補依賴、權限或 context"],
] as const;

