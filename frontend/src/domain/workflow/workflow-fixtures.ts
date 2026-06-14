export const workflowRows = [
  ["Discuss / Explore / Prototype / Spec / Usage / Tkt", "DEFINING", "Definition Package"],
  ["Ready boundary", "READY", "Assignment + runtime preflight"],
  ["Dev / Unit-test / Integration-test / E2E-test", "RUNNING", "Execution evidence complete"],
  ["Review", "REVIEW", "Approve triggers release / reject to DEFINING"],
  ["release", "REVIEW", "Release complete -> DONE"],
] as const;
