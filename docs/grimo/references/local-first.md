# Local-First Reference Notes

**Purpose:** Preserve research context behind Grimo's local-first product stance. PRD should only keep the product conclusions.

## Product Takeaways For Grimo

1. Local-first is not just "runs locally"; it means the user owns the working data and can keep using the product when network, provider accounts, or external services are unavailable.
2. Grimo's local store should be the source of truth for Project, Task, Definition Package, workflow evidence, Review Materials, Release evidence and Learning proposals.
3. External issue trackers, cloud sync, provider sessions and PR comments are projections or execution channels, not the canonical record.
4. Local-first is not local-only. Future sync and collaboration can exist, but they must not weaken local speed, offline readability, export/backup and user ownership.
5. Full local-first sync is not MVP scope. Multi-device or multi-user sync requires explicit design for offline/online transition, conflict policy, partial sync, schema migration, permissions, audit and encryption.

## Source Notes

### indie.tw — Local-First

Source: <https://indie.tw/docs/local-first/>

Key points:

- Local-first combines cloud-like collaboration with desktop-like ownership.
- Seven criteria: speed, multi-device, offline use, real-time collaboration, longevity, privacy/security and ownership.
- Git is a useful analogy for developer tools: every endpoint has complete data and history can support conflict comparison and recovery.
- CRDTs are presented as a promising foundation for collaboration, but the product value is ownership plus smooth UX, not the technology by itself.

Grimo implication:

- Treat workflow evidence as user-owned local data.
- Make export/backup and offline readability product requirements, not implementation extras.

### ExplainThis — 本地優先設計

Source: <https://www.explainthis.io/zh-hant/swe/local-first>

Key points:

- Pure cloud creates hidden dependency on remote machines; a failure elsewhere can make the user's own computer unusable.
- Local-first means software can run locally without network, while still supporting safe, private and durable collaboration.
- Local-first is not local-only.
- OT and CRDT are common approaches for collaborative state, but they imply eventual consistency and domain-specific collaboration semantics.

Grimo implication:

- MVP can be local-first without building collaboration sync.
- Future sync must be designed as a separate capability, not assumed from SQLite or workflow persistence.

### Ink & Switch — Local-First Software

Source: <https://www.inkandswitch.com/local-first/>

Key points:

- Users should own their data in spite of the cloud.
- Cloud services offer collaboration; old-fashioned local apps offer ownership. Local-first tries to combine both.

Grimo implication:

- Cloud/provider integration should never be the only place where Grimo stores task or workflow history.

### Bytemash — Linear Local-First Rabbit Hole

Source: <https://bytemash.net/posts/i-went-down-the-linear-rabbit-hole/>

Key points:

- Linear feels fast because interaction writes happen locally first, then sync happens asynchronously.
- Moving data to the client removes network latency from the interaction path.
- Sync is not trivial: offline/online transitions, conflict resolution, partial synchronization, cached schema migrations and distributed security/access control are major work.
- Good fit areas include developer tools, personal productivity apps, mobile offline support and collaborative applications.

Grimo implication:

- Grimo is a good local-first fit because it is a developer tool and workflow workbench.
- MVP should focus on local source of truth and instant local read/write UX, while deferring sync.

### Hacker News Discussion

Source: <https://news.ycombinator.com/item?id=44833834>

Key points:

- Query-driven or partial sync can avoid syncing an entire database.
- Sync and latency hiding are domain-specific; there is no magic drop-in solution.
- Browser local storage and web offline behavior have platform-specific sharp edges.
- Local-first can be valuable for low-bandwidth/offline users, but its trade-offs must be explicit.

Grimo implication:

- Do not over-promise full local-first sync in MVP.
- If future sync enters scope, decide entity-by-entity what can auto-merge and what must require human conflict resolution.
