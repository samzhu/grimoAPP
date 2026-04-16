package io.github.samzhu.grimo.core.domain;

/**
 * Identifier for a CLI-backed agent provider.
 *
 * <p>The MVP surface is fixed by the PRD to three providers:
 * <ul>
 *   <li>{@link #CLAUDE} — Anthropic Claude Code CLI.</li>
 *   <li>{@link #CODEX} — OpenAI Codex CLI.</li>
 *   <li>{@link #GEMINI} — Google Gemini CLI.</li>
 * </ul>
 *
 * <p>Extensibility (plugin-style provider registration) is post-MVP —
 * S001 D5 documents why a closed enum is correct at this stage.
 */
public enum ProviderId {
    CLAUDE,
    CODEX,
    GEMINI
}
