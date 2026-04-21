package io.github.samzhu.grimo.session.domain;

import java.time.Instant;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Composable query criteria for session events (S018 redesign).
 *
 * @param from             null = no start bound
 * @param to               null = no end bound
 * @param messageTypes     null = all types
 * @param excludeSynthetic null = include all; true = exclude compaction summaries
 * @param lastN            null = all events; N = last N events
 * @param keyword          null = no search; non-null = message_content LIKE search
 * @param page             null = no pagination
 * @param pageSize         null = no pagination
 * @param branch           null = root; branch-scoped events
 */
public record EventFilter(
    @Nullable Instant from,
    @Nullable Instant to,
    @Nullable Set<MessageType> messageTypes,
    @Nullable Boolean excludeSynthetic,
    @Nullable Integer lastN,
    @Nullable String keyword,
    @Nullable Integer page,
    @Nullable Integer pageSize,
    @Nullable String branch
) {
    public static EventFilter all() {
        return new EventFilter(null, null, null, null, null, null, null, null, null);
    }

    public static EventFilter realOnly() {
        return new EventFilter(null, null, null, true, null, null, null, null, null);
    }

    public static EventFilter forBranch(String branch) {
        return new EventFilter(null, null, null, null, null, null, null, null, branch);
    }
}
