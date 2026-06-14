# Grimo Design Language

**版本：** 1.0  
**最後更新：** 2026-05-28  
**適用範圍：** Grimo AI 開發工作台 · 深色模式

以 docs/grimo/design/DESIGN.md 為主, 此為繁體中文閱讀版

---

## 目錄

1. [品牌基調](#1-品牌基調)
2. [色彩系統](#2-色彩系統)
3. [字體排印](#3-字體排印)
4. [間距與圓角](#4-間距與圓角)
5. [陰影與發光](#5-陰影與發光)
6. [任務狀態語義](#6-任務狀態語義)
7. [元件規格](#7-元件規格)
8. [版面與響應式](#8-版面與響應式)
9. [動畫](#9-動畫)
10. [圖示規範](#10-圖示規範)
11. [文案語言](#11-文案語言)
12. [存取性](#12-存取性)

---

## 1. 品牌基調

### 核心隱喻

> **魔法典籍工作台（Arcane Workbench）**  
> 深邃午夜、星圖軌道、羽毛筆書寫符文——精準與神秘並存的開發者控制平台。

Grimo 的視覺語言來自兩種張力的平衡：

| 工程側 | 魔法側 |
|--------|--------|
| 等寬字體、精確數據、細線框 | 星空背景、青藍發光、羽毛筆品牌 |
| 密度適中、可掃描、資訊優先 | 狀態高亮、脈衝動畫、星點紋理 |
| Linear / GitHub 的操作感 | 獨特的夜色沉浸感 |

### 品牌識別元件

**Logo**：開放書本 + 羽毛筆 + 星座軌道環  
- 書本象徵知識積累與任務記錄  
- 羽毛筆代表 AI 代理書寫執行  
- 軌道環節點呼應工作流程配方步驟  

**主色**：星藍青 `oklch(72% 0.160 220)` — 作為所有主動態、選中態、強調色

**魔法強度**：7 / 10（克制的大氣感，不干擾操作密度）

---

## 2. 色彩系統

所有顏色以 `oklch` 定義，確保感知均勻度。

### 2.1 底層背景

| Token | 值 | 用途 |
|-------|----|------|
| `--bg` | `oklch(8% 0.030 245)` | App 最底層背景 |
| `--surface` | `oklch(13% 0.028 245)` | 卡片、抽屜、對話框 |
| `--surface-2` | `oklch(17% 0.025 245)` | 輸入框、次要面板 |
| `--surface-3` | `oklch(21% 0.022 245)` | 控制項、標籤 |
| `--surface-hover` | `oklch(19% 0.024 245)` | hover 態背景 |

所有 hue 值固定在 245（偏向寒藍），確保整體色調統一。

### 2.2 前景文字

| Token | 值 | 用途 |
|-------|----|------|
| `--fg` | `oklch(93% 0.010 245)` | 主要文字 |
| `--fg-2` | `oklch(74% 0.018 245)` | 次要文字 |
| `--muted` | `oklch(52% 0.022 245)` | 說明文字、標籤 |
| `--muted-2` | `oklch(36% 0.016 245)` | 極淡、佔位 |

### 2.3 邊框

| Token | 值 | 用途 |
|-------|----|------|
| `--border` | `oklch(22% 0.020 245)` | 預設邊框 |
| `--border-subtle` | `oklch(16% 0.018 245)` | 極細分隔線 |
| `--border-strong` | `oklch(30% 0.025 245)` | hover / 強調邊框 |

### 2.4 強調色（Accent）

三種可切換的強調色，透過 Tweaks 面板選擇：

| 名稱 | Token | 值 | 情境 |
|------|-------|-----|------|
| 星藍（預設） | `--accent` | `oklch(72% 0.160 220)` | 主動作、選中、REVIEW |
| 薰紫 | — | `oklch(65% 0.150 290)` | 代理執行風格 |
| 琥珀 | — | `oklch(72% 0.150 55)` | 溫暖注意力風格 |

**Accent 衍生 token（以星藍為例）：**

```css
--accent-dim:  oklch(58% 0.130 220)   /* 輔助、次要邊框 */
--accent-bg:   oklch(16% 0.045 220)   /* 選中態底色 */
--accent-glow: oklch(72% 0.160 220 / 0.18)  /* 發光 box-shadow */
```

### 2.5 語義色

| Token | 值 | 用途 |
|-------|----|------|
| `--green` | `oklch(66% 0.130 152)` | 成功、通過、READY |
| `--purple` | `oklch(65% 0.150 290)` | 代理執行中、RUNNING |
| `--warn` | `oklch(70% 0.140 58)` | 警告、風險說明 |
| `--danger` | `oklch(60% 0.160 28)` | 錯誤、BLOCKED |

**原則**：語義色 chroma 全部鎖定在 0.13–0.16，lightness 在 60–72%，確保在深色背景上對比充足且視覺協調。

---

## 3. 字體排印

### 字族

```css
--font-sans: 'Noto Sans TC', 'PingFang TC', 'Microsoft JhengHei', system-ui, sans-serif;
--font-mono: 'JetBrains Mono', 'SF Mono', 'Cascadia Code', ui-monospace, monospace;
```

**Sans**（Noto Sans TC）：用於所有 UI 文字、中文內容、按鈕、標籤  
**Mono**（JetBrains Mono）：用於任務 ID、CLI 命令、程式碼、路徑、數值

### 尺寸階層

| 用途 | 大小 | 字重 | 說明 |
|------|------|------|------|
| 頁面標題 | 20px | 700 | 任務完整頁面 h1 |
| 看板標題 | 15px | 600 | 頂欄品牌名、視圖標題 |
| 卡片標題 | 12.5px | 500 | 任務卡片主標題 |
| 內文 | 13px | 400 | 描述、對話 |
| 次要文字 | 12px | 400 | metadata、說明 |
| 小標籤 | 11–11.5px | 600 | section 標題（大寫）|
| 微型標籤 | 10–10.5px | 400–500 | 任務 ID、時間戳記 |

### 排印規則

- **Section 標題**：全大寫 + `letter-spacing: 0.05–0.08em` + `--muted` 色
- **任務 ID**：等寬字體 `font-family: var(--font-mono)`，色 `--muted`
- **品質分數**：等寬字體 22px 700，色依分數語義
- **line-height**：內文 1.65，標題 1.35–1.45，列表行 1.5
- **text-wrap: pretty**：套用於多行段落文字

---

## 4. 間距與圓角

### 間距尺度（8pt Grid）

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

**使用原則**：元件內部間距用 `--sp-2` 到 `--sp-4`；元件之間用 `--sp-3` 到 `--sp-6`；頁面邊距用 `--sp-4` 到 `--sp-6`。

### 圓角

| Token | 值 | 用途 |
|-------|-----|------|
| `--r-sm` | 5px | 小標籤、微型元件 |
| `--r` | 8px | 預設（卡片、按鈕、輸入框）|
| `--r-lg` | 12px | 對話框、面板、大卡片 |
| `--r-xl` | 16px | 對話框外框 |
| `--r-full` | 9999px | 徽章、狀態標籤 |

---

## 5. 陰影與發光

Grimo 用發光（glow）取代傳統陰影，呼應星光視覺語言。

### 陰影層級

```css
--shadow-sm:  0 1px 3px oklch(0% 0 0 / 0.4)
--shadow:     0 2px 8px oklch(0% 0 0 / 0.5)
--shadow-lg:  0 4px 24px oklch(0% 0 0 / 0.6)
```

### 發光效果

```css
/* 選中態 / 主動作 */
--glow-accent: 0 0 0 1px var(--accent-glow), 0 0 12px var(--accent-glow)

/* READY 狀態 */
--glow-ready:  0 0 0 1px oklch(66% 0.130 152 / 0.2), 0 0 8px oklch(66% 0.130 152 / 0.12)
```

### 發光使用規則

| 情境 | 效果 |
|------|------|
| 任務卡片 hover | `box-shadow: --shadow` + `border-color: --border-strong` |
| 任務卡片選中 | `box-shadow: --glow-accent` + `border-color: --accent-dim` |
| Primary 按鈕 hover | `box-shadow: 0 0 12px var(--accent-glow)` |
| REVIEW 欄位標頭 | `box-shadow: 0 0 8px {state-color}25` |
| RUNNING 脈衝點 | `box-shadow: 0 0 6px var(--s-running)` |
| 對話框 | `box-shadow: --shadow-lg, 0 0 40px var(--accent-glow2)` |

---

## 6. 任務狀態語義

### 看板狀態

| 狀態 | 中文 | 色彩 Token | 設計意涵 |
|------|------|-----------|---------|
| `BACKLOG` | 待辦 | `--s-backlog` `oklch(44% 0.018 245)` | 低優先、靜態 |
| `DEFINING` | 定義中 | `--s-defining` `oklch(70% 0.130 58)` | 琥珀暖色，需要補充 |
| `READY` | 就緒 | `--s-ready` `oklch(66% 0.130 152)` | 綠色，等待派遣 |
| `RUNNING` | 執行中 | `--s-running` `oklch(65% 0.150 290)` | 紫色，代理活躍 |
| `REVIEW` | 等待檢視 | `--s-review` `oklch(72% 0.160 220)` | 青藍，最高注意力 |
| `DONE` | 已完成 | `--s-done` `oklch(54% 0.090 152)` | 暗綠，靜態完結 |
| `BLOCKED` | 已阻塞 | `--s-blocked` `oklch(60% 0.160 28)` | 紅色，需人工介入 |

每個狀態有對應的暗色背景版本，命名規則：`--s-{state}-bg`

### 狀態 Badge 規格

```
外框：border-radius: --r-full
內容：• {label}（圓點 + 中文狀態名）
圓點：5×5px border-radius:50%，同狀態色，帶 box-shadow glow
字級：10.5px（sm）/ 12px（md）
```

### REVIEW 特殊處理

REVIEW 是最重要的人工操作閘門，在以下位置獲得額外視覺強調：

- 頂欄顯示「N 等待檢視」脈衝標籤
- 導航列 badge 帶有角標數量
- 看板欄位標頭帶藍色頂邊框 + 發光
- 聚焦佈局中獨立展示為大卡片區塊
- 詳情抽屜底部顯示檢視行動欄

---

## 7. 元件規格

### 7.1 任務卡片

```
寬度：240px（Kanban 欄位中）/ 全寬（清單模式）
內距：10px 12px 10px 14px（balanced 密度）
左邊框：3px 寬，顏色 = 對應狀態色
背景：--surface

結構：
  ┌────────────────────────────────────┐
  │ [GRM-NNN]              [步驟]       │← 10.5px mono / 可選
  │ 任務標題（最多2行 ellipsis）           │← 12.5px 500
  │ [標籤1] [標籤2]                      │← 標籤列
  │ [來源] · [時間] · 💬N · ✦N.N       │← meta 行 11px
  └────────────────────────────────────┘

RUNNING 專屬：右上角 6px 脈衝圓點（position: absolute）
選中態：border-color: --accent-dim；background: --accent-bg；glow
```

**密度變體（Tweaks 控制）：**

| 密度 | 上下內距 | 使用情境 |
|------|---------|---------|
| compact | 8px | 高密度資訊查看 |
| balanced | 10px（預設）| 日常操作 |
| comfortable | 14px | 展示、大螢幕 |

### 7.2 按鈕

| 變體 | 用途 | 背景 | 文字 | 邊框 |
|------|------|------|------|------|
| `primary` | 主要動作 | `--accent` | 深色 | 同色 |
| `success` | 核准、就緒 | `--green` | 深色 | 同色 |
| `ghost` | 次要動作 | 透明 | `--fg-2` | `--border` |
| `danger` | 退回、刪除 | 透明 | `--danger` | `--danger` |
| `icon` | 工具按鈕 | 透明 | `--muted` | 無 |

**尺寸：**
- 預設：`padding: 8px 16px`，`font-size: 12.5px`
- `btn-sm`：`padding: 3px 12px`，`font-size: 11.5px`

### 7.3 詳情抽屜

```
寬度：clamp(420px, 32vw, 520px)
位置：右側固定，display:flex 列方向
背景：oklch(12% 0.028 245 / 0.97) + backdrop-filter: blur(8px)
左邊框：1px solid --border

結構：
  ┌─ Header（48px）─────────────────────┐
  │  任務詳情         [↗] [📌] [✕]      │
  ├─ Body（scroll）─────────────────────┤
  │  • 任務 ID + 狀態徽章 + 來源         │
  │  • 工作流程步驟麵包屑               │
  │  • 標題 + 描述                      │
  │  • 代理資訊行                       │
  │  • 執行中進度（RUNNING 才顯示）      │
  │  • 檢視行動欄（REVIEW 才顯示）       │
  │  • 執行品質（Quality Score）         │
  │  • 驗收標準 / 檢視資料              │
  │  • 執行證據                         │
  │  • 待補缺口（有缺口才顯示）          │
  │  • 標籤                             │
  ├─ Footer（sticky）────────────────────┤
  │  [主要行動按鈕] 或 檢視狀態說明      │
  └─────────────────────────────────────┘
```

### 7.4 品質分數（Quality Score）

```
容器：flex row，padding 12px，background --surface-2，border-radius --r

左側：大數字（22px 700 mono）+ "/ 10"（10px muted）
右側：
  - 進度條（4px 高，圓角）
  - 品質閘門說明文字（11px）
  - 評分來源說明（10.5px muted）

顏色對應：
  score ≥ 9.0：--green（閘門通過）
  score ≥ 7.0：--warn（需修正）
  score < 7.0：--danger（未達標）
```

### 7.5 證據條目（Evidence Item）

```
外框：border-radius --r；左邊框 3px（依類型上色）
類型：pass（綠）/ fail（紅）/ warn（琥珀）/ screenshot（紫）/ info（青）

結構：[圖示 14px] [標籤 + 命令 + 說明] [時間戳]
命令：font-mono 10.5px，前綴 $
```

### 7.6 狀態徽章（State Badge）

```css
display: inline-flex; align-items: center; gap: 5px;
padding: 2px 8px; border-radius: var(--r-full);
font-size: 10.5px; font-weight: 600; letter-spacing: 0.02em;
border: 1px solid {state-color}30;
background: {state-bg};
color: {state-color};
```

### 7.7 標籤 Chip

每個標籤有固定色彩映射（依語意）：

| 標籤 | 色彩 |
|------|------|
| 後端 | `#5B8AF0`（藍） |
| 修復 | `#F06C6C`（紅） |
| 功能 | `#6CF0C8`（青綠）|
| 效能 | `#F09B6C`（橙） |
| DevOps | `#6C9BF0`（水藍）|
| 維護 | `#8A9B8A`（灰綠）|

格式：`background: {color}15`，`border: 1px solid {color}35`，`color: {color}`

---

## 8. 版面與響應式

### 桌面版面（≥ 920px）

```
┌────────────────────────────────────────────────────────────┐
│  TOPBAR  48px                                              │
├──────┬──────────────────────────────────────┬─────────────┤
│      │                                      │             │
│ NAV  │          主內容區                     │  詳情抽屜   │
│ RAIL │     (看板 / 視圖)                     │   440px    │
│ 56px │                                      │  (可關閉)   │
│      │                                      │             │
└──────┴──────────────────────────────────────┴─────────────┘
```

### 導航列尺寸

| 狀態 | 寬度 | 顯示內容 |
|------|------|---------|
| 收合（預設）| 56px | 圖示 + 角標 |
| 展開 | 200px | 圖示 + 中文標籤 + 角標 |

點擊底部收合按鈕切換；展開狀態下有平滑過渡 `transition: width 0.25s ease`。

### 看板欄位

```css
/* Kanban */
.kanban-col { width: 240px; flex-shrink: 0; }
.kanban-board { display: flex; gap: 16px; overflow-x: auto; }

/* 捲軸：6px 高，使用 --border-strong 色，radius 3px */
```

### 全頁詳情

任務詳情完整頁面使用雙欄佈局：
- **主欄**（flex: 1）：工作流程步驟、標題描述、驗收標準、執行證據
- **側欄**（280px 固定）：品質分數、任務資訊、標籤

---

## 9. 動畫

### 脈衝動畫（Pulse）

用於：RUNNING 狀態指示點、REVIEW 等待檢視標籤、頂欄提醒

```css
@keyframes pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 4px currentColor; }
  50%       { opacity: 0.4; box-shadow: 0 0 10px currentColor; }
}
animation: pulse 1.8s ease-in-out infinite;
```

### 過渡時間

| 用途 | 時長 | Easing |
|------|------|--------|
| 顏色、背景、邊框 | 0.15s | ease |
| 詳情抽屜滑入 | 0.25s | ease |
| 導航列展開 | 0.25s | ease |
| 品質分數條填充 | 0.6s | ease |
| Chat typing dots | 1.2s | ease-in-out（stagger 150ms）|

### 星空背景

90 個偽隨機星點，使用確定性種子生成（每次載入相同）：

```js
// 星點：radial-gradient 1px 或 1.5px 圓點
// 亮度：rgba(180, 215, 255, 0.15–0.65)
// 分布：全視口均勻散布
// 星雲：兩個 ellipse radial-gradient 在頂部和右下
```

---

## 10. 圖示規範

### 規格

- 來源：內嵌 SVG（自繪），無外部圖示庫依賴
- 尺寸系統：12 / 13 / 14 / 15 / 16 / 18px（依場景）
- 筆觸：`strokeWidth: 1.5`，`strokeLinecap: round`，`strokeLinejoin: round`
- 預設：`fill: none`，部分圖示用 `fill: currentColor`

### 圖示清單

| 名稱 | 用途 |
|------|------|
| `board` | 任務看板導航 |
| `review` | 等待檢視導航 / 檢視決策 |
| `blocker` | 待處理導航 / 警告 |
| `chat` | Chat 導航 |
| `workflow` | 工作流程導航 |
| `project` | 專案導航 |
| `check` | 通過、驗收、核准 |
| `xCircle` | 退回、失敗、關閉帶圓 |
| `x` | 關閉、取消 |
| `plus` | 新增 |
| `search` | 搜尋 |
| `pin` | 固定抽屜 |
| `expand` | 完整頁面 |
| `star` / `sparkle` | 品質分數 ✦ |
| `agent` | 代理指派 |
| `github` | GitHub 來源 |
| `zap` | 啟動、快速動作 |
| `lock` | 阻塞、缺少權限 |
| `retry` | 重試 |
| `send` | 送出 Chat |

### 品牌 Logo（GrimoLogo）

```
viewBox: 0 0 32 32
元素：
  - 星座軌道環（circle，strokeOpacity 0.5）
  - 三個節點圓點（頂/左/右，fill currentColor）
  - 頂部四芒星（path fill currentColor）
  - 翻開書本（path，stroke，stroke-linejoin: round）
  - 書脊中線（line，strokeOpacity 0.6）
  - 羽毛筆（path，fill currentColor，opacity 0.9）
```

---

## 11. 文案語言

### 產品術語表

| 概念 | 正確用語（繁中）| 避免使用 |
|------|----------------|---------|
| 任務狀態集合 | 任務看板 | board、面板 |
| 需要人工檢視 | 等待檢視 | review queue |
| 需要人工介入 | 待處理 | blockers |
| 執行代理 | 代理 / Agent | AI、機器人 |
| 代理提供者 | Claude Code / Codex / Gemini CLI | adapter、provider |
| 工作流程 | 工作流程配方 | pipeline、flow |
| 驗收條件 | 驗收標準 | acceptance criteria |
| 執行記錄 | 執行證據 | logs、artifacts |
| 收尾證據 | 完結摘要 | Release evidence |
| 失敗的任務 | 已阻塞 | error、failed |
| 任務來源 | 來源（GitHub / Chat / 手動建立）| source type |

### 動作按鈕文案

| 狀態 | 主要動作 |
|------|---------|
| BACKLOG | 使用 Chat 定義 |
| DEFINING | 繼續定義 |
| READY | 啟動預檢 |
| RUNNING | 查看執行記錄 |
| REVIEW | 核准 / 退回修改 |
| DONE | 建立追蹤任務 |
| BLOCKED | 重試 / 修復 |

### 文案原則

1. **不暴露技術實作**：說「本地任務引擎」，不說「POC adapter」
2. **動作明確**：按鈕說「核准通過」，不說「OK」
3. **狀態可解釋**：阻塞理由必須說明修復路徑
4. **品質分數有語義**：「✦ 品質閘門通過」而非只顯示數字

---

## 12. 存取性

### 鍵盤導航

- 所有互動元素可 Tab 聚焦
- 對話框：焦點陷阱（focus trap），Esc 關閉
- 導航列：方向鍵或 Tab 在項目間移動
- 詳情抽屜：Esc 關閉（未固定時）

### ARIA

- 頂欄搜尋框：`aria-label="搜尋任務"`
- 導航列：`aria-label="主導航"`
- 圖示按鈕：`title` 屬性提供說明
- 對話框：`role="dialog"`，`aria-modal="true"`
- 狀態更新：Toast 通知應帶 `aria-live="polite"`

### 色彩對比

- 主文字 `--fg` 對 `--bg`：> 12:1（WCAG AAA）
- 次要文字 `--fg-2` 對 `--surface`：> 7:1（WCAG AA）
- 弱化文字 `--muted` 對 `--surface`：> 4.5:1（WCAG AA）
- 狀態色在各自 `--s-{state}-bg` 上：全部 > 4.5:1

**原則**：所有狀態區分不僅依賴顏色，同時使用形狀（圓點）、標籤文字、圖示三重編碼。

### 點擊目標

- 所有按鈕最小高度 28px，推薦 32px+
- 導航列項目最小高度 36px
- 任務卡片最小高度 60px

---

## 附錄：CSS Token 快速參照

```css
/* 完整 token 列表見 grimo-styles.css :root 區段 */

/* 最常用的 */
var(--accent)        /* 主強調色 */
var(--surface)       /* 卡片底色 */
var(--border)        /* 預設邊框 */
var(--fg)            /* 主文字 */
var(--muted)         /* 說明文字 */
var(--font-mono)     /* 等寬字體 */
var(--r)             /* 預設圓角 8px */
var(--t)             /* 預設過渡 0.15s ease */
```

---

*本文件由 Grimo 設計系統 v1.0 自動整理，與 `grimo-styles.css` Token 定義保持同步。*
