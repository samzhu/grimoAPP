# grimo-runtime — 容器化 CLI 代理執行環境
#
# 為何用 node:20-slim（Debian/glibc）而非 Alpine：
#   Claude Code 原生二進位自 v2.1.63 起依賴 glibc posix_getdents symbol，
#   Alpine 的 musl libc 不提供此 symbol，導致硬 crash。
#   來源：https://github.com/anthropics/claude-code/issues/29559
#
# 為何用 curl 安裝器而非 npm 安裝 claude-code：
#   npm 套件 @anthropic-ai/claude-code 在 v2.1.15 deprecated，且缺 sdk.mjs entry point。
#   來源：https://github.com/anthropics/claude-code/issues/20402

FROM node:20-slim

# 系統依賴：git（子代理 worktree 操作）、curl + ca-certificates（claude-code 安裝器）
RUN apt-get update && apt-get install -y --no-install-recommends \
        git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Codex CLI（固定版本）
RUN npm install -g @openai/codex@0.121.0

# Gemini CLI（固定版本）
RUN npm install -g @google/gemini-cli@0.38.1

# Claude Code（原生安裝器 — 安裝至 ~/.local/bin/claude）
# curl 安裝器目前不支援指定版本，安裝 latest。建置日期版本記錄於 README。
RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="/root/.local/bin:${PATH}"

# S003 bind-mount 掛載點
WORKDIR /work

# 長期存活容器（S003 用 sleep infinity 保持容器供 docker exec 呼叫）
CMD ["sleep", "infinity"]
