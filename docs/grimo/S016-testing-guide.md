# S016 MVP 人工驗證測試指南

## 架構概覽

```
~/.grimo/skills/<name>/SKILL.md     ← Grimo 統一管理（來源）
        ↓ grimo chat 啟動時自動投影
<workdir>/.claude/skills/<name>/SKILL.md  ← Claude Code 原生載入
        ↓
Claude Code 啟動 → 掃描 .claude/skills/ → skill 可用
```

## Session 儲存位置

Claude CLI 自己管理 session transcript：

```
~/.claude/projects/<workdir-hash>/<sessionId>.jsonl
```

- 每個工作目錄對應一個 hash 子目錄
- `grimo chat --resume` 透過 Claude CLI 的 `--continue` flag 自動接回**同目錄**最近一次 session
- Grimo 自己不存 session（S011 POC 驗證 Claude CLI 原生支援即足夠）

## Skill 機制

### 儲存結構

| 路徑 | 用途 |
|------|------|
| `~/.grimo/skills/<name>/SKILL.md` | Grimo 管理的 skill 來源（agentskills.io 格式） |
| `~/.grimo/skills/.state.json` | 啟用/停用狀態持久化 |
| `<workdir>/.claude/skills/<name>/SKILL.md` | 投影副本（chat 啟動時自動建立） |

### SKILL.md 格式（agentskills.io 標準）

```yaml
---
name: greet
description: "A greeting skill for MVP verification"
metadata:
  author: samzhu
  version: 1.0.0
---
Skill 指令內容（Markdown body）
```

- `name` 必填，1-64 字元，`[a-z0-9-]`，不可 `-` 開頭/結尾
- `name` 必須與父目錄名稱一致
- `description` 必填

### 觸發方式

Claude Code 掃描 `<workdir>/.claude/skills/` 後，skill 成為可用的知識。在對話中直接引用 skill 名稱或描述即可觸發 Claude 參考該 skill 的指令內容。

## 測試步驟

### 步驟 0：編譯

```bash
./gradlew bootJar
```

### 步驟 1：建立測試 Skill

```bash
mkdir -p ~/.grimo/skills/greet
cat > ~/.grimo/skills/greet/SKILL.md << 'EOF'
---
name: greet
description: "A greeting skill for MVP verification"
---
When the user asks you to greet, respond with exactly:
"Hello from Grimo Skill! MVP verification passed."
EOF
```

### 步驟 2：驗證 Skill 管理（AC-1, AC-2, AC-4）

```bash
# 列出 skills
java -jar build/libs/grimo-*.jar skill list
# 預期：
#   Skills (1 found):
#     greet                enabled    A greeting skill for MVP verification

# 停用
java -jar build/libs/grimo-*.jar skill disable greet
java -jar build/libs/grimo-*.jar skill list
# 預期：greet  disabled

# 重新啟用
java -jar build/libs/grimo-*.jar skill enable greet
java -jar build/libs/grimo-*.jar skill list
# 預期：greet  enabled

# 錯誤處理 — 不存在的 skill
java -jar build/libs/grimo-*.jar skill enable nonexistent
# 預期 stderr：Skill not found: nonexistent

# 錯誤處理 — 無子命令
java -jar build/libs/grimo-*.jar skill
# 預期：印出 usage 說明
```

### 步驟 3：驗證 Skill 投影 + 對話（AC-3 + S007）

```bash
java -jar build/libs/grimo-*.jar chat
```

啟動後另開終端確認投影：

```bash
# 確認 skill 已投影
cat .claude/skills/greet/SKILL.md
# 預期：內容與 ~/.grimo/skills/greet/SKILL.md 一致
```

回到對話視窗：

```
you> 請用 greet skill 打招呼
# 預期：回應包含 "Hello from Grimo Skill! MVP verification passed."

you> /exit
```

### 步驟 4：驗證 Session 恢復（S011）

```bash
java -jar build/libs/grimo-*.jar chat --resume
```

```
you> 我剛才問了什麼？
# 預期：引用 greet 相關內容（證明接回了上次 session）

you> /exit
```

## 驗證檢查清單

| # | 項目 | 指令 | 預期結果 | 通過 |
|---|------|------|----------|------|
| 1 | Skill list | `skill list` | 顯示 greet + enabled + 描述 | |
| 2 | Skill disable | `skill disable greet` → `skill list` | greet 顯示 disabled | |
| 3 | Skill enable | `skill enable greet` → `skill list` | greet 顯示 enabled | |
| 4 | 錯誤：不存在 | `skill enable nonexistent` | stderr 印出錯誤 | |
| 5 | 錯誤：無子命令 | `skill` | 印出 usage | |
| 6 | Skill 投影 | `chat` 後檢查 `.claude/skills/greet/` | 檔案存在且內容一致 | |
| 7 | 對話功能 | chat 中對話 | Claude 正常回應 | |
| 8 | Skill 使用 | 要求使用 greet skill | 回應引用 skill 指令 | |
| 9 | Session 恢復 | `chat --resume` 後問上次內容 | 引用先前對話 | |

## 清理

測試完畢後可選擇清理投影檔案：

```bash
rm -rf .claude/skills/greet/
rm -rf ~/.grimo/skills/greet/
rm ~/.grimo/skills/.state.json
```
