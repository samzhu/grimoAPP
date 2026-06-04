# Grimo Design Language

**Version:** 1.0  
**Last updated:** 2026-05-28  
**Scope:** Grimo AI Development Workbench · Dark Mode

---

## Table of Contents

1. [Brand Identity](#1-brand-identity)
2. [Color System](#2-color-system)
3. [Typography](#3-typography)
4. [Spacing & Radius](#4-spacing--radius)
5. [Elevation & Glow](#5-elevation--glow)
6. [Task State Semantics](#6-task-state-semantics)
7. [Component Specs](#7-component-specs)
8. [Layout & Responsive](#8-layout--responsive)
9. [Motion](#9-motion)
10. [Iconography](#10-iconography)
11. [Content & Copy](#11-content--copy)
12. [Accessibility](#12-accessibility)

---

## 1. Brand Identity

### Core Metaphor

> **The Arcane Workbench**  
> Deep midnight, star-chart orbits, a quill writing runes — a developer control plane that balances precision with mystery.

Grimo's visual language lives at the intersection of two tensions:

| Engineering Side | Arcane Side |
|-----------------|-------------|
| Monospaced IDs, precise data, hairline borders | Starfield background, cyan glow, quill-and-book brand |
| Scannable density, information-first hierarchy | State highlights, pulse animations, star-dot texture |
| Operational feel of Linear / GitHub | Unique immersive night-sky atmosphere |

### Brand Mark Elements

**Logo:** Open book + quill pen + constellation orbital ring  
- Book → knowledge accumulation and task history  
- Quill → AI agent writing and executing  
- Orbital ring nodes → workflow recipe steps  

**Primary color:** Star-blue cyan `oklch(72% 0.160 220)` — used for all active states, selections, and primary actions

**Magic intensity:** 7 / 10 (restrained atmosphere; does not compromise operational density)

---

## 2. Color System

All colors are defined in `oklch` for perceptual uniformity.

### 2.1 Backgrounds

| Token | Value | Usage |
|-------|-------|-------|
| `--bg` | `oklch(8% 0.030 245)` | Deepest app background |
| `--surface` | `oklch(13% 0.028 245)` | Cards, drawers, dialogs |
| `--surface-2` | `oklch(17% 0.025 245)` | Inputs, secondary panels |
| `--surface-3` | `oklch(21% 0.022 245)` | Controls, chips |
| `--surface-hover` | `oklch(19% 0.024 245)` | Hover state background |

All hue values are anchored at 245 (cool blue) for tonal consistency.

### 2.2 Foreground / Text

| Token | Value | Usage |
|-------|-------|-------|
| `--fg` | `oklch(93% 0.010 245)` | Primary text |
| `--fg-2` | `oklch(74% 0.018 245)` | Secondary text |
| `--muted` | `oklch(52% 0.022 245)` | Captions, labels |
| `--muted-2` | `oklch(36% 0.016 245)` | Placeholder, ghost |

### 2.3 Borders

| Token | Value | Usage |
|-------|-------|-------|
| `--border` | `oklch(22% 0.020 245)` | Default border |
| `--border-subtle` | `oklch(16% 0.018 245)` | Very faint dividers |
| `--border-strong` | `oklch(30% 0.025 245)` | Hover / emphasis border |

### 2.4 Accent Colors

Three swappable accents, selectable from the Tweaks panel:

| Name | Token | Value | Mood |
|------|-------|-------|------|
| Star-blue (default) | `--accent` | `oklch(72% 0.160 220)` | Primary actions, selection, REVIEW |
| Lavender | — | `oklch(65% 0.150 290)` | Agent-execution style |
| Amber | — | `oklch(72% 0.150 55)` | Warm attention style |

**Derived accent tokens (star-blue example):**

```css
--accent-dim:  oklch(58% 0.130 220)   /* Secondary borders, dimmed accents */
--accent-bg:   oklch(16% 0.045 220)   /* Selected state fill */
--accent-glow: oklch(72% 0.160 220 / 0.18)  /* Glow box-shadow */
```

### 2.5 Semantic Colors

| Token | Value | Usage |
|-------|-------|-------|
| `--green` | `oklch(66% 0.130 152)` | Success, pass, READY |
| `--purple` | `oklch(65% 0.150 290)` | Agent running, RUNNING |
| `--warn` | `oklch(70% 0.140 58)` | Warning, risk notes |
| `--danger` | `oklch(60% 0.160 28)` | Error, BLOCKED |

**Principle:** All semantic colors share chroma in the 0.13–0.16 range and lightness in 60–72%, ensuring consistent contrast and harmony on the dark background.

---

## 3. Typography

### Font Stack

```css
--font-sans: 'Noto Sans TC', 'PingFang TC', 'Microsoft JhengHei', system-ui, sans-serif;
--font-mono: 'JetBrains Mono', 'SF Mono', 'Cascadia Code', ui-monospace, monospace;
```

**Sans** (Noto Sans TC): All UI text, body copy, buttons, labels  
**Mono** (JetBrains Mono): Task IDs, CLI commands, code snippets, paths, numeric scores

### Type Scale

| Role | Size | Weight | Notes |
|------|------|--------|-------|
| Page title | 20px | 700 | Full-page task detail h1 |
| Section heading | 15px | 600 | Topbar brand, view titles |
| Card title | 12.5px | 500 | Task card primary title |
| Body | 13px | 400 | Descriptions, chat |
| Secondary | 12px | 400 | Metadata, captions |
| Micro label | 11–11.5px | 600 | Section headers (uppercased) |
| Caption | 10–10.5px | 400–500 | Task IDs, timestamps |

### Typography Rules

- **Section headers:** All-caps + `letter-spacing: 0.05–0.08em` + `--muted` color
- **Task IDs:** Monospace, color `--muted`
- **Quality score:** Monospace 22px/700, color follows score semantic
- **Line height:** Body 1.65 · Headings 1.35–1.45 · List rows 1.5
- **`text-wrap: pretty`** applied to multi-line paragraph text

---

## 4. Spacing & Radius

### Spacing Scale (8pt Grid)

```css
--sp-1:  4px
--sp-2:  8px
--sp-3:  12px
--sp-4:  16px
--sp-5:  20px
--sp-6:  24px
--sp-8:  32px
--sp-10: 40px
--sp-12: 48px
```

**Rules:** Inner component padding: `--sp-2` to `--sp-4`. Between components: `--sp-3` to `--sp-6`. Page margins: `--sp-4` to `--sp-6`.

### Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `--r-sm` | 5px | Small chips, micro components |
| `--r` | 8px | Default (cards, buttons, inputs) |
| `--r-lg` | 12px | Dialogs, panels, large cards |
| `--r-xl` | 16px | Dialog outer shell |
| `--r-full` | 9999px | Badges, state labels, pills |

---

## 5. Elevation & Glow

Grimo uses **glow** instead of traditional drop shadows, evoking starlight.

### Shadow Scale

```css
--shadow-sm:  0 1px 3px oklch(0% 0 0 / 0.4)
--shadow:     0 2px 8px oklch(0% 0 0 / 0.5)
--shadow-lg:  0 4px 24px oklch(0% 0 0 / 0.6)
```

### Glow Effects

```css
/* Selected state / primary action */
--glow-accent: 0 0 0 1px var(--accent-glow), 0 0 12px var(--accent-glow)

/* READY state */
--glow-ready:  0 0 0 1px oklch(66% 0.130 152 / 0.2), 0 0 8px oklch(66% 0.130 152 / 0.12)
```

### When to Use Glow

| Context | Effect |
|---------|--------|
| Task card hover | `box-shadow: --shadow` + `border-color: --border-strong` |
| Task card selected | `box-shadow: --glow-accent` + `border-color: --accent-dim` |
| Primary button hover | `box-shadow: 0 0 12px var(--accent-glow)` |
| REVIEW column header | `box-shadow: 0 0 8px {state-color}25` |
| RUNNING pulse dot | `box-shadow: 0 0 6px var(--s-running)` |
| Dialog | `box-shadow: --shadow-lg, 0 0 40px var(--accent-glow2)` |

---

## 6. Task State Semantics

### Board States

| State | Color Token | Value | Design Meaning |
|-------|-------------|-------|----------------|
| `BACKLOG` | `--s-backlog` | `oklch(44% 0.018 245)` | Low urgency, static |
| `DEFINING` | `--s-defining` | `oklch(70% 0.130 58)` | Warm amber, needs input |
| `READY` | `--s-ready` | `oklch(66% 0.130 152)` | Green, awaiting dispatch |
| `RUNNING` | `--s-running` | `oklch(65% 0.150 290)` | Purple, agent active |
| `REVIEW` | `--s-review` | `oklch(72% 0.160 220)` | Cyan, highest attention |
| `DONE` | `--s-done` | `oklch(54% 0.090 152)` | Dim green, static record |
| `BLOCKED` | `--s-blocked` | `oklch(60% 0.160 28)` | Red, requires human |

Each state has a matching dim background: `--s-{state}-bg`

### State Badge Spec

```
Shape:     border-radius: --r-full  
Contents:  • {label}  (dot + state name)
Dot:       5×5px, border-radius: 50%, same state color, glow box-shadow
Font:      10.5px (sm) / 12px (md), weight 600
```

### REVIEW Special Treatment

REVIEW is the critical human approval gate and receives extra visual emphasis:

- Topbar shows pulsing "N pending review" badge
- Nav rail shows numeric badge
- Kanban column header has blue top-border + glow
- Focus layout promotes REVIEW tasks to large attention cards
- Detail drawer shows approve/reject action bar at the bottom

---

## 7. Component Specs

### 7.1 Task Card

```
Width:        240px (in Kanban column) / full-width (list mode)
Padding:      10px 12px 10px 14px (balanced density)
Left border:  3px, color = matching state color
Background:   --surface

Structure:
  ┌────────────────────────────────────────┐
  │ [GRM-NNN]                 [Step]       │  ← 10.5px mono / optional
  │ Task title (2-line clamp + ellipsis)   │  ← 12.5px 500
  │ [label1] [label2]                      │  ← label row
  │ [source] · [time] · 💬N · ✦N.N       │  ← meta row 11px
  └────────────────────────────────────────┘

RUNNING:  6px pulse dot top-right (position: absolute, animated)
Selected: border-color: --accent-dim; background: --accent-bg; glow
```

**Density variants (Tweaks-controlled):**

| Density | Vertical padding | Use case |
|---------|-----------------|----------|
| compact | 8px | High-density information scanning |
| balanced | 10px (default) | Daily operation |
| comfortable | 14px | Presentation, large screens |

### 7.2 Buttons

| Variant | Purpose | Background | Text | Border |
|---------|---------|------------|------|--------|
| `primary` | Main action | `--accent` | Dark | Same |
| `success` | Approve, ready | `--green` | Dark | Same |
| `ghost` | Secondary action | Transparent | `--fg-2` | `--border` |
| `danger` | Reject, delete | Transparent | `--danger` | `--danger` |
| `icon` | Toolbar buttons | Transparent | `--muted` | None |

**Sizes:**
- Default: `padding: 8px 16px`, `font-size: 12.5px`
- `btn-sm`: `padding: 3px 12px`, `font-size: 11.5px`

### 7.3 Detail Drawer

```
Width:      clamp(420px, 32vw, 520px)
Position:   Right edge, flex column
Background: oklch(12% 0.028 245 / 0.97) + backdrop-filter: blur(8px)
Left edge:  1px solid --border

Structure:
  ┌─ Header (48px) ────────────────────────┐
  │  Task Detail              [↗] [📌] [✕] │
  ├─ Body (scrollable) ────────────────────┤
  │  • Task ID + state badge + source      │
  │  • Workflow step breadcrumb            │
  │  • Title + description                 │
  │  • Agent assignment row                │
  │  • Running progress (RUNNING only)     │
  │  • Review action bar (REVIEW only)     │
  │  • Quality score                       │
  │  • Acceptance criteria / review mats   │
  │  • Evidence list                       │
  │  • Definition gaps (when present)      │
  │  • Labels                              │
  ├─ Footer (sticky) ──────────────────────┤
  │  [Primary action button] or review msg │
  └────────────────────────────────────────┘
```

### 7.4 Quality Score

```
Container: flex row, padding 12px, background --surface-2, border-radius --r

Left side:  Large number (22px 700 mono) + "/ 10" (10px muted)
Right side:
  - Progress bar (4px tall, rounded)
  - Gate status message (11px)
  - Score source note (10.5px muted)

Color mapping:
  score ≥ 9.0 → --green  (gate passed)
  score ≥ 7.0 → --warn   (needs fix)
  score < 7.0 → --danger (below threshold)
```

### 7.5 Evidence Item

```
Container: border-radius --r, 3px left border (type-specific color)
Types:     pass (green) / fail (red) / warn (amber) / screenshot (purple) / info (cyan)

Structure: [icon 14px] [label + command + note] [timestamp]
Command:   font-mono 10.5px, prefixed with $
```

### 7.6 State Badge

```css
display: inline-flex;
align-items: center;
gap: 5px;
padding: 2px 8px;
border-radius: var(--r-full);
font-size: 10.5px;
font-weight: 600;
letter-spacing: 0.02em;
border: 1px solid {state-color}30;
background: {state-bg};
color: {state-color};
```

### 7.7 Label Chip

Each label has a fixed semantic color mapping:

| Label | Color |
|-------|-------|
| Backend | `#5B8AF0` (blue) |
| Bug / Fix | `#F06C6C` (red) |
| Feature | `#6CF0C8` (teal) |
| Performance | `#F09B6C` (orange) |
| DevOps | `#6C9BF0` (sky blue) |
| Maintenance | `#8A9B8A` (gray-green) |

Format: `background: {color}15`, `border: 1px solid {color}35`, `color: {color}`

---

## 8. Layout & Responsive

### Desktop Layout (≥ 920px)

```
┌────────────────────────────────────────────────────────────┐
│  TOPBAR  48px                                              │
├──────┬──────────────────────────────────────┬─────────────┤
│      │                                      │             │
│ NAV  │          Main Content                │   Detail    │
│ RAIL │     (Board / Views)                  │   Drawer    │
│ 56px │                                      │   440px     │
│      │                                      │ (closeable) │
└──────┴──────────────────────────────────────┴─────────────┘
```

### Nav Rail Sizes

| State | Width | Content |
|-------|-------|---------|
| Collapsed (default) | 56px | Icons + badges |
| Expanded | 200px | Icons + labels + badges |

Toggle via bottom collapse button; smooth `transition: width 0.25s ease`.

### Kanban Columns

```css
.kanban-col   { width: 240px; flex-shrink: 0; }
.kanban-board { display: flex; gap: 16px; overflow-x: auto; }

/* Scrollbar: 6px, --border-strong color, 3px radius */
```

### Full-Page Detail

Two-column layout:
- **Main column** (flex: 1): Workflow breadcrumb, title, description, acceptance criteria, evidence
- **Sidebar** (280px fixed): Quality score, task metadata, labels

### Three Board Layouts (Tweaks)

| Layout | Description |
|--------|-------------|
| **Kanban** | Standard 6-column horizontal-scroll board |
| **List** | Grouped vertical sections by state, REVIEW first |
| **Focus** | REVIEW tasks as prominent attention cards at top; mini-board below |

---

## 9. Motion

### Pulse Animation

Used for: RUNNING state indicator, REVIEW topbar badge, attention signals

```css
@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 4px currentColor; }
  50%       { opacity: 0.4; box-shadow: 0 0 10px currentColor; }
}
animation: pulse 1.8s ease-in-out infinite;
```

### Transition Durations

| Context | Duration | Easing |
|---------|----------|--------|
| Color, background, border | 0.15s | ease |
| Detail drawer slide-in | 0.25s | ease |
| Nav rail expand | 0.25s | ease |
| Quality score bar fill | 0.6s | ease |
| Chat typing dots | 1.2s | ease-in-out (150ms stagger) |

### Starfield Background

90 pseudo-random star dots generated via deterministic seed (identical on every load):

```js
// Each star: radial-gradient 1px or 1.5px dot
// Brightness: rgba(180, 215, 255, 0.15–0.65)
// Distribution: uniform across full viewport
// Nebula: two ellipse radial-gradients at top and bottom-right
```

---

## 10. Iconography

### Spec

- Source: Inline SVG (hand-authored), no external icon library dependency
- Size system: 12 / 13 / 14 / 15 / 16 / 18px (context-dependent)
- Stroke: `strokeWidth: 1.5`, `strokeLinecap: round`, `strokeLinejoin: round`
- Default: `fill: none`; some icons use `fill: currentColor`

### Icon Inventory

| Name | Usage |
|------|-------|
| `board` | Task board nav |
| `review` | Review nav / approve action |
| `blocker` | Blockers nav / warning |
| `chat` | Chat nav |
| `workflow` | Workflow nav |
| `project` | Projects nav |
| `check` | Pass, acceptance, approve |
| `xCircle` | Reject, fail, close with ring |
| `x` | Close, cancel |
| `plus` | Create new |
| `search` | Search |
| `pin` | Pin drawer |
| `expand` | Open full page |
| `star` / `sparkle` | Quality score ✦ |
| `agent` | Agent assignment |
| `github` | GitHub source |
| `zap` | Launch, quick action |
| `lock` | Blocked, missing permissions |
| `retry` | Retry |
| `send` | Send chat message |

### Brand Logo (GrimoLogo)

```
viewBox: 0 0 32 32
Elements:
  - Constellation orbital ring (circle, strokeOpacity 0.5)
  - Three node dots (top/left/right, fill currentColor)
  - Four-pointed star at top (path, fill currentColor)
  - Open book (path, stroke, stroke-linejoin: round)
  - Spine center line (line, strokeOpacity 0.6)
  - Quill pen (path, fill currentColor, opacity 0.9)
```

---

## 11. Content & Copy

### Product Terminology

| Concept | Preferred term | Avoid |
|---------|---------------|-------|
| Task state set | Task Board | board, panel |
| Needs human review | Pending Review | review queue |
| Needs human action | Needs Human / Blockers | errors, failed |
| Execution agent | Agent | AI, bot, robot |
| Agent provider | Claude Code / Codex / Gemini CLI | adapter, provider |
| Task lifecycle | Workflow Recipe | pipeline, flow |
| Completion conditions | Acceptance Criteria | conditions |
| Execution artifacts | Evidence | logs, artifacts |
| Task release evidence | Release evidence | wrap |
| Unresolved items | Definition Gaps | issues, unknowns |
| Task origin | Source (GitHub / Chat / Manual) | source type |

### Primary Action Copy by State

| State | Primary action label |
|-------|---------------------|
| BACKLOG | Use Chat to Define |
| DEFINING | Continue Definition |
| READY | Run Preflight |
| RUNNING | View Execution Log |
| REVIEW | Approve / Reject |
| DONE | Create Follow-up Task |
| BLOCKED | Retry / Repair |

### Copy Principles

1. **Never expose implementation details** — say "Local task engine," not "POC adapter"
2. **Actions must be explicit** — "Approve & Complete," not "OK"
3. **States must be explainable** — blockers must include a repair path
4. **Quality scores have semantics** — show "✦ Quality gate passed," not just the number
5. **Work enters Grimo; agents don't start automatically** — copy should reinforce the human confirmation gate

---

## 12. Accessibility

### Keyboard Navigation

- All interactive elements are Tab-focusable
- Dialogs: focus trap; Esc closes
- Nav rail: Tab or arrow keys between items
- Detail drawer: Esc closes (when not pinned)
- Board cards: Enter or Space to select

### ARIA

- Topbar search: `aria-label="Search tasks"`
- Nav rail: `aria-label="Main navigation"`
- Icon-only buttons: `title` attribute for tooltip
- Dialogs: `role="dialog"`, `aria-modal="true"`
- State updates: Toast notifications should carry `aria-live="polite"`

### Color Contrast

| Pairing | Ratio | Standard |
|---------|-------|----------|
| `--fg` on `--bg` | > 12:1 | WCAG AAA |
| `--fg-2` on `--surface` | > 7:1 | WCAG AA |
| `--muted` on `--surface` | > 4.5:1 | WCAG AA |
| State colors on `--s-{state}-bg` | > 4.5:1 | WCAG AA |

**Principle:** All state distinctions use three-layer encoding — color + shape (dot) + text label — never color alone.

### Touch Targets

- All buttons: minimum height 28px, recommended 32px+
- Nav rail items: minimum height 36px
- Task cards: minimum height 60px

---

## Appendix: Quick Token Reference

```css
/* Full token list: see grimo-styles.css :root block */

/* Most commonly used */
var(--accent)        /* Primary accent color */
var(--surface)       /* Card background */
var(--border)        /* Default border */
var(--fg)            /* Primary text */
var(--muted)         /* Caption text */
var(--font-mono)     /* Monospace font stack */
var(--r)             /* Default radius: 8px */
var(--t)             /* Default transition: 0.15s ease */
```

---

*This document is maintained alongside `grimo-styles.css` and should be updated whenever design tokens change.*
