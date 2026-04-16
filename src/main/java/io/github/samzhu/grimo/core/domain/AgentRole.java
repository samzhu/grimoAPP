package io.github.samzhu.grimo.core.domain;

/**
 * The role an agent plays in a Grimo interaction.
 *
 * <ul>
 *   <li>{@link #MAIN} — the conversational entry point on the host (read-only;
 *       see Main Agent in the glossary).</li>
 *   <li>{@link #SUB} — an isolated executor spawned in a Docker sandbox with
 *       a bind-mounted worktree (YOLO write inside the sandbox).</li>
 *   <li>{@link #JURY_MEMBER} — one of N parallel reviewers in a jury
 *       fan-out (see Jury in the glossary).</li>
 * </ul>
 *
 * <p>Represented as an {@code enum} rather than a sealed interface because
 * MVP roles are stateless identifiers — S001 D4 documents the rationale
 * (per-role payload, if it ever emerges, can upgrade to a sealed hierarchy
 * in a later spec without breaking callers).
 */
public enum AgentRole {
    MAIN,
    SUB,
    JURY_MEMBER
}
