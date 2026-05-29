# Webwright / Visual Testing 使用筆記

> 這份筆記整理 Grimo 前端 redesign 過程中的實際使用經驗。  
> 目前本 repo 的 `npm run test:visual` / `npm run test:visual:update` 是 Playwright visual regression scripts；Webwright 是更上層的 terminal-native web agent 思路與 harness，不是目前 package.json 直接安裝的 npm script 名稱。

## Webwright 是什麼

Webwright 的核心概念是：讓 agent 使用 terminal、本地 workspace，以及可丟棄的 browser session 來完成 web task。

官方介紹的重點：

- Agent 不是只預測下一個 click / type / scroll，而是可以寫程式啟動 browser、檢查頁面、保存 log / screenshot / output，再重跑。
- Browser session 可以是 disposable 的；壞掉或卡住時，可以關掉重開。
- 最終產物不只是「完成一次操作」，而是一組可重用的 script、log、screenshots、trace、result。
- Webwright 的 harness 很小，核心是 runner、model endpoint、terminal environment。

這和我們這次做 UI 調整時的模式很接近：每次改完 frontend，不靠肉眼臨時點一點，而是用固定 command 產生固定 viewport 截圖，再用 artifact 檢查差異。

## Grimo 目前實際使用的 commands

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run test:visual
```

用途：

- 跑 Playwright visual regression。
- 對 desktop / tablet / mobile viewport 產生截圖。
- 和 `frontend/e2e/task-workbench.visual.spec.ts-snapshots/` 裡的 baseline 比對。
- 如果失敗，會在 `frontend/test-results/` 產生 actual screenshot、diff、trace、error context。

```bash
cd /Users/samzhu/workspace/github-samzhu/grimoAPP/frontend
npm run test:visual:update
```

用途：

- 當 UI 變更是刻意的，先人工/agent 檢查 actual screenshot。
- 確認畫面符合預期後，更新 snapshot baseline。
- 更新後必須再跑一次 `npm run test:visual`，確認新的 baseline 穩定。

## 這次 redesign 的實際流程

1. 先改 UI。
2. 跑 `npm run build`，確認 TypeScript / Vite build 沒壞。
3. 跑 `npm run test:visual`。
4. 如果 visual fail，先看 `frontend/test-results/.../*-actual.png`，不要直接更新 snapshot。
5. 確認是預期差異，或修掉 layout 問題。
6. 跑 `npm run test:visual:update` 更新 baseline。
7. 再跑 `npm run test:visual`，最後要看到 all passed。

這次有幾個實際案例：

- Focus 區加上後，mobile 被兩欄卡片撐到 468px，從 actual screenshot 直接看出橫向溢出。
- mobile board 改成 list layout 後，snapshot 明確記錄新的手機版資訊架構。
- 主選單改成 overlay-until-pinned 後，補了 Playwright behavior test，避免之後又回到打開選單就推擠版面。
- `待處理焦點` 加收合/展開後，也補了 behavior test，確認按鈕真的會隱藏和還原 focus cards。

## 使用心得

### 1. Visual test 很適合守 UI 佈局，但不能取代設計判斷

Snapshot 只能回答「現在跟 baseline 是否不同」，不能回答「這樣設計好不好」。  
所以流程上要先看 actual screenshot，再決定是修 UI 還是更新 baseline。

### 2. 每個 snapshot update 都要有理由

好的理由：

- 新增預期功能，例如 Focus + Board。
- 手機版刻意從 Kanban 改為 List。
- Header toolbar 修正成 search 與 `新增 Task` 同列。
- 新增收合/展開控制，畫面高度自然改變。

不好的理由：

- 沒看圖，只是為了讓測試過。
- 不知道為什麼畫面變了，但先更新。
- 把 regression 當成新 baseline。

### 3. actual / diff / trace 是 debug 的入口

失敗時先看：

- `*-actual.png`：目前畫面。
- `*-diff.png`：和 baseline 差在哪。
- `trace.zip`：必要時回放操作。
- `error-context.md`：Playwright 對失敗點的描述。

這比手動猜 CSS 快很多。

### 4. Visual + behavior test 要搭配

只靠 screenshot 不一定能抓互動語義。  
這次除了 snapshots，也補了行為檢查：

- 主選單未 pin 時只是 overlay；按 pin 後才變 pinned layout。
- `待處理焦點` 可以收合，再展開。

這類狀態語義用 Playwright assertion 比純 screenshot 更清楚。

### 5. 對 agent 工作流很友善

這套流程特別適合 coding agent，原因是：

- command 固定，可以重跑。
- artifact 固定，agent 可以打開 actual screenshot 檢查。
- 失敗有 diff 和 trace，不需要只靠文字錯誤。
- snapshot update 是明確動作，可以要求 agent 說明為什麼更新。

這也是 Webwright 思路有價值的地方：web work 不只是一次性點瀏覽器，而是把探索、驗證、修正和 artifacts 留在 workspace 裡。

## 建議的使用規則

- 每次前端 layout / responsive / visual state 變更後，都跑 `npm run test:visual`。
- 第一次 fail 不代表錯；先看 actual screenshot。
- 如果是預期 UI 變更，跑 `npm run test:visual:update`。
- update 後必須再跑 `npm run test:visual`。
- 若牽涉互動語義，不只更新 snapshot，也補 Playwright assertion。
- 不要把 `test-results/` 當成果提交；成果是 source、spec、snapshots。

## 和 Webwright 的關係

目前 Grimo frontend 的 visual workflow 是 Playwright-based，不是直接使用 Webwright harness。

但它已經符合 Webwright 強調的幾個實務精神：

- terminal first：用 command 重現瀏覽器檢查。
- workspace artifacts：截圖、diff、trace 留在本地。
- reusable programs：測試規格和 scripts 可以反覆執行。
- disposable browser sessions：每次測試都由 Playwright 啟動乾淨頁面。
- verification before done：不是看起來可以就結束，而是跑到 visual gate passed。

未來如果要更接近 Webwright，可以把這套流程包成更完整的 agent skill：

- 自動跑 visual gate。
- 自動列出 changed screenshots。
- 自動產生「哪些 snapshot 更新、為什麼」的摘要。
- 將常見 viewport / selector / screenshot inspection 固化成 reusable tool。
- 在前端需求、UI/UX 調整、設計語言討論、browser comments 出現時，同步更新 `docs/grimo/design/frontend-design-context.md`。

已落地為專案 skill：

- `.agents/skills/frontend-design-context/SKILL.md`
- `.agents/skills/frontend-design-context/references/context-schema.md`
- `.agents/skills/frontend-design-context/scripts/visual-snapshot-summary.py`
