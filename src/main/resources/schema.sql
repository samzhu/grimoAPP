-- Grimo conversation turn table (S017)
-- Append-only per-turn recording with rich metadata
CREATE TABLE IF NOT EXISTS grimo_conversation_turn (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    turn_sequence INT NOT NULL,
    user_message CLOB NOT NULL,
    assistant_message CLOB,
    model VARCHAR(100),
    duration_ms BIGINT,
    tokens_in BIGINT,
    tokens_out BIGINT,
    finish_reason VARCHAR(20),
    provider VARCHAR(20) DEFAULT 'claude',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_grimo_turn_session
    ON grimo_conversation_turn(session_id, turn_sequence);
