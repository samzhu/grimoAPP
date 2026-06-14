CREATE TABLE IF NOT EXISTS projects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    workspace_path TEXT NOT NULL UNIQUE,
    workflow_recipe_id TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS project_workflow_roles (
    project_id TEXT NOT NULL,
    role_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    primary_steps TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (project_id, role_id),
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- table: tasks
-- 用途: 保存使用者在 Project 內建立的一件工作，讓 Task board 能依 Project 掃描 BACKLOG 任務。
-- owner: projects.id。每個 Task 必須掛在 Project 底下，避免孤兒任務。
-- 不存: active workflow step、quality score、comments、acceptance/gaps/evidence projection。
CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL CHECK (source IN ('manual')),
    state TEXT NOT NULL CHECK (state IN ('BACKLOG', 'DEFINING', 'READY', 'RUNNING', 'REVIEW', 'DONE')),
    workflow_recipe_id TEXT NOT NULL,
    labels TEXT NOT NULL DEFAULT '[]',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_project_updated
ON tasks(project_id, updated_at DESC);

-- table: task_workflows
-- 用途: 保存 Task 建立當下複製出的 immutable workflow copy，讓後續執行不受 Project recipe 或 workflow file 修改影響。
-- owner: tasks.id。每個 Task 在建立時取得一份 workflow copy。
-- 不存: active execution state、quality score attempt details、Task outer state。
CREATE TABLE IF NOT EXISTS task_workflows (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL UNIQUE,
    source_type TEXT NOT NULL CHECK (source_type IN ('RECIPE', 'FILE')),
    source_ref TEXT NOT NULL,
    source_hash TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id)
);

-- table: task_workflow_steps
-- 用途: 保存 Task Workflow 底下的 immutable ordered step metadata，例如 Discuss、Explore、Dev。
-- owner: task_workflows.id。step label / task_state 來自建立 Task 當下的 Project workflow definition。
-- 不存: active execution state、Quality Loop attempt details、chat comments。
CREATE TABLE IF NOT EXISTS task_workflow_steps (
    id TEXT PRIMARY KEY,
    task_workflow_id TEXT NOT NULL,
    step_key TEXT NOT NULL,
    step_label TEXT NOT NULL,
    task_state TEXT NOT NULL,
    step_order INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_workflow_id) REFERENCES task_workflows(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_workflow_steps_key
ON task_workflow_steps(task_workflow_id, step_key);

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_workflow_steps_order
ON task_workflow_steps(task_workflow_id, step_order);

-- table: task_workflow_runs
-- 用途: 保存一個 Task 的 workflow execution context；S004 建立 Task 時不寫入 active run。
-- owner: tasks.id。Project ownership 必須透過 tasks.project_id 驗證，不由本表重複存 project_id。
-- 不存: board/list 的 Task outer state、currentStep projection、quality score attempt details。
CREATE TABLE IF NOT EXISTS task_workflow_runs (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    task_workflow_id TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('ACTIVE', 'PASSED', 'BLOCKED', 'CANCELLED')),
    started_at TEXT NOT NULL,
    completed_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id),
    FOREIGN KEY (task_workflow_id) REFERENCES task_workflows(id)
);

CREATE INDEX IF NOT EXISTS idx_task_workflow_runs_task
ON task_workflow_runs(task_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_workflow_runs_task_state
ON task_workflow_runs(task_id, state, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_workflow_runs_one_active
ON task_workflow_runs(task_id)
WHERE state = 'ACTIVE';

-- table: task_workflow_run_steps
-- 用途: 保存 workflow run 底下的 ordered execution steps，例如 Discuss、Explore、Dev。
-- owner: task_workflow_runs.id。step label / task_state 來自 Task Workflow，讀取時不重新從 catalog 或 workflow file 推算。
-- 不存: Quality Loop attempt details、chat comments、Review Materials artifact body。
CREATE TABLE IF NOT EXISTS task_workflow_run_steps (
    id TEXT PRIMARY KEY,
    workflow_run_id TEXT NOT NULL,
    step_key TEXT NOT NULL,
    step_label TEXT NOT NULL,
    task_state TEXT NOT NULL,
    step_order INTEGER NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'ACTIVE', 'PASSED', 'BLOCKED', 'SKIPPED')),
    started_at TEXT,
    completed_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (workflow_run_id) REFERENCES task_workflow_runs(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_workflow_run_steps_run_key
ON task_workflow_run_steps(workflow_run_id, step_key);

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_workflow_run_steps_run_order
ON task_workflow_run_steps(workflow_run_id, step_order);

CREATE INDEX IF NOT EXISTS idx_task_workflow_run_steps_run_state_order
ON task_workflow_run_steps(workflow_run_id, state, step_order);

-- table: task_workflow_quality_runs
-- 用途: 保存某個 workflow run step 的 Review -> Rating -> Gate -> Fix 嘗試紀錄。
-- owner: task_workflow_run_steps.id。每個 attempt 是 immutable evidence row。
-- 不存: 大型 artifact 內容、完整 Task Conversation Thread、final Review Materials bundle。
CREATE TABLE IF NOT EXISTS task_workflow_quality_runs (
    id TEXT PRIMARY KEY,
    workflow_run_step_id TEXT NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    output_summary TEXT NOT NULL DEFAULT '',
    output_ref TEXT,
    review_summary TEXT NOT NULL DEFAULT '',
    quality_score REAL,
    fix_summary TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (workflow_run_step_id) REFERENCES task_workflow_run_steps(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_task_workflow_quality_runs_step_attempt
ON task_workflow_quality_runs(workflow_run_step_id, attempt);

CREATE INDEX IF NOT EXISTS idx_task_workflow_quality_runs_step_latest
ON task_workflow_quality_runs(workflow_run_step_id, attempt DESC);
