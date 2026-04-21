-- Grimo Schema (S018 — full rewrite: Project / Task / Session / Session Event)
-- S018 redesign: DROP old S017 tables, then CREATE new schema.
-- Safe: ~/.grimo/db/ can be deleted to reset.

-- Drop circular FK before dropping tables (S023: session ↔ event circular ref)
ALTER TABLE IF EXISTS grimo_session DROP CONSTRAINT IF EXISTS fk_session_current_event;

-- Drop old tables in reverse FK order
DROP TABLE IF EXISTS grimo_session_event;
DROP TABLE IF EXISTS grimo_session;
DROP TABLE IF EXISTS grimo_task;
DROP TABLE IF EXISTS grimo_project;
DROP SEQUENCE IF EXISTS grimo_task_number_seq;

-- 1. Project
CREATE TABLE IF NOT EXISTS grimo_project (
    id          VARCHAR(12)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    work_dir    VARCHAR(500) NOT NULL,
    description VARCHAR(2000),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- 2. Task (generic work item — not limited to dev tasks)
CREATE SEQUENCE IF NOT EXISTS grimo_task_number_seq START WITH 1;

CREATE TABLE IF NOT EXISTS grimo_task (
    id            VARCHAR(12)  PRIMARY KEY,
    task_number   INT          NOT NULL UNIQUE,
    project_id    VARCHAR(12),
    title         VARCHAR(500) NOT NULL,
    body          CLOB,
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    priority      VARCHAR(10)  DEFAULT 'MEDIUM',
    labels_json   CLOB,
    source_type   VARCHAR(20),
    source_ref    VARCHAR(200),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    closed_at     TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES grimo_project(id)
);

CREATE INDEX IF NOT EXISTS idx_task_project_status
    ON grimo_task(project_id, status);

-- 3. Session (projection — materialized summary)
-- Note: current_event_id FK added via ALTER TABLE after grimo_session_event is created (circular dep)
CREATE TABLE IF NOT EXISTS grimo_session (
    id                VARCHAR(36)  PRIMARY KEY,
    session_type      VARCHAR(20)  NOT NULL,
    project_id        VARCHAR(12),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    turn_count        INT          DEFAULT 0,
    total_tokens_in   BIGINT       DEFAULT 0,
    total_tokens_out  BIGINT       DEFAULT 0,
    total_duration_ms BIGINT       DEFAULT 0,
    event_version     BIGINT       DEFAULT 0,
    current_event_id  VARCHAR(36),
    work_dir          VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    last_active_at    TIMESTAMP    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES grimo_project(id)
);

CREATE INDEX IF NOT EXISTS idx_session_project
    ON grimo_session(project_id, session_type);

-- 4. Session Event (aligned with Spring AI Session naming + S023 Adjacency List tree)
CREATE TABLE IF NOT EXISTS grimo_session_event (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    session_id      VARCHAR(36)  NOT NULL,
    parent_event_id VARCHAR(36),
    message_type    VARCHAR(20)  NOT NULL,
    message_content TEXT,
    message_data    TEXT,
    provider        VARCHAR(20),
    model           VARCHAR(100),
    metadata        TEXT,
    synthetic       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_event_session
        FOREIGN KEY (session_id) REFERENCES grimo_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_event_parent
        FOREIGN KEY (parent_event_id) REFERENCES grimo_session_event(id)
);

CREATE INDEX IF NOT EXISTS idx_session_event_session_ts
    ON grimo_session_event(session_id, created_at);

-- Circular FK: grimo_session.current_event_id → grimo_session_event.id
ALTER TABLE grimo_session ADD CONSTRAINT IF NOT EXISTS fk_session_current_event
    FOREIGN KEY (current_event_id) REFERENCES grimo_session_event(id);
