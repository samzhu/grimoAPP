import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { taskLabelOptions } from "../src/domain/task/task-labels";
import { tasks } from "../src/domain/task/task-fixtures";

test("task fixtures use prototype-defined labels", () => {
  for (const task of tasks) {
    for (const label of task.labels) {
      expect(taskLabelOptions).toContain(label);
    }
  }
});

const viewports = [
  { name: "desktop-1366", width: 1366, height: 768 },
  { name: "desktop-1440", width: 1440, height: 900 },
  { name: "mobile-390", width: 390, height: 844 },
  { name: "tablet-820", width: 820, height: 1180 },
] as const;

const visualProject = {
  id: "01HZVISUALPROJECT",
  name: "visual Project",
  description: "Task dialog visual context",
  projectPath: "/tmp/grimo-visual-project",
  workflowRecipeId: "web-service-development",
  workflowRecipeName: "Web 服務開發",
  status: "ACTIVE",
  createdAt: "2026-06-04T00:00:00Z",
  updatedAt: "2026-06-04T00:00:00Z",
  workflowRoles: [],
};

function toTaskResponse(task: (typeof tasks)[number]) {
  return {
    id: task.id,
    projectId: visualProject.id,
    title: task.title,
    body: task.body ?? task.description,
    description: task.description,
    state: task.state,
    source: task.source,
    workflowRecipeId: visualProject.workflowRecipeId,
    workflowSummary: task.workflowSummary,
    updatedAt: "2026-06-04T00:00:00Z",
    acceptance: task.acceptance,
    gaps: task.gaps,
    evidence: task.evidence,
    labels: task.labels,
    commentCount: task.commentCount,
  };
}

async function openVisualTaskWorkbench(page: Page) {
  await page.route("**/api/projects", async (route) => {
    await route.fulfill({ json: { content: [visualProject] } });
  });
  await page.route(`**/api/projects/${visualProject.id}/tasks`, async (route) => {
    await route.fulfill({ json: { content: tasks.map(toTaskResponse) } });
  });
  await page.addInitScript((projectId) => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: projectId, isClosed: false }),
    );
  }, visualProject.id);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
}

async function openEmptyVisualProject(page: Page) {
  await page.route("**/api/projects", async (route) => {
    await route.fulfill({ json: { content: [visualProject] } });
  });
  await page.route(`**/api/projects/${visualProject.id}/tasks`, async (route) => {
    await route.fulfill({ json: { content: [] } });
  });
  await page.addInitScript((projectId) => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: projectId, isClosed: false }),
    );
  }, visualProject.id);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
}

test.describe("Task workbench visual gate", () => {
  for (const viewport of viewports) {
    test(`board baseline ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await openVisualTaskWorkbench(page);
      await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
      await expect(page.getByRole("button", { name: "新增 Task" })).toBeVisible();
      await expect(page.getByPlaceholder("搜尋任務 / 關鍵字")).toBeVisible();
      if (viewport.width > 920) {
        await expect(page.locator(".app-header")).toHaveJSProperty("offsetHeight", 52);
        await expect(page.locator(".brand-mark img")).toHaveJSProperty("offsetHeight", 38);
      }
      await expect(page.locator(".task-card.selected")).toHaveCount(0);
      await expect(page.locator(".task-card", { hasText: "GRM-144" }).getByText("task-forming", { exact: true })).toHaveCount(0);
      await expect(page.locator(".task-card", { hasText: "GRM-144" }).locator(".badge.label").first()).toHaveText("frontend");
      await expect(page).toHaveScreenshot(`task-workbench-${viewport.name}.png`, {
        fullPage: true,
      });
    });
  }

  test("main navigation overlays until pinned", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);

    await page.getByRole("button", { name: "展開主選單" }).click();
    await expect(page.locator(".workspace-shell.nav-open")).toHaveCount(1);
    await expect(page.locator(".workspace-shell.nav-pinned")).toHaveCount(0);

    await page.getByRole("button", { name: "固定主選單" }).click();
    await expect(page.locator(".workspace-shell.nav-pinned")).toHaveCount(1);
  });

  test("AC-S011-6: Task Details Pane uses canonical selector", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);

    await page.locator(".task-card").first().click();

    await expect(page.locator(".task-details-pane")).toBeVisible();
  });

  test("attention focus can collapse and expand", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);

    await expect(page.getByRole("heading", { name: "待處理焦點" })).toBeVisible();
    await expect(page.getByRole("button", { name: "查看焦點任務 GRM-188" })).toBeVisible();

    await page.getByRole("button", { name: "收合" }).click();
    await expect(page.getByRole("button", { name: "查看焦點任務 GRM-188" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "展開", exact: true })).toBeVisible();

    await page.getByRole("button", { name: "展開", exact: true }).click();
    await expect(page.getByRole("button", { name: "查看焦點任務 GRM-188" })).toBeVisible();
  });

  test("chat action returns to task-forming chat", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);

    await expect(page.getByText("補上下文")).toHaveCount(0);
    await page.locator(".focus-task-card").first().getByRole("button", { name: "Chat", exact: true }).click();

    await expect(page.getByRole("heading", { name: "工作形成 Chat" })).toBeVisible();
    await expect(page.getByText("GRM-188", { exact: true })).toBeVisible();
  });

  test("desktop board is viewport bounded", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);

    const metrics = await page.evaluate(() => {
      const columnBody = document.querySelector(".column-body");

      return {
        viewportHeight: window.innerHeight,
        pageHeight: document.documentElement.scrollHeight,
        columnOverflowY: columnBody
          ? window.getComputedStyle(columnBody).overflowY
          : "",
      };
    });

    expect(metrics.pageHeight).toBeLessThanOrEqual(metrics.viewportHeight + 2);
    expect(metrics.columnOverflowY).toBe("auto");
  });

  test("selected task opens detail drawer", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);
    await page.getByRole("button", { name: /執行前設定與本機能力力檢查|執行前設定與本機能力檢查/ }).click();
    await expect(page.getByRole("heading", { name: "任務詳情" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "執行前設定與本機能力檢查" })).toBeVisible();
    await expect(page.getByText("來源", { exact: true })).toBeVisible();
    await expect(page.locator(".detail-head .badge.task-id")).toHaveText("GRM-201");
    await expect(page.locator(".detail-head .badge.state")).toHaveText("READY");
    await expect(page.locator(".detail-head .badge-row").getByText("Ready boundary", { exact: true })).toHaveCount(0);
    await expect(page).toHaveScreenshot("task-detail-drawer.png", {
      fullPage: true,
    });
  });

  test("create task dialog baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openEmptyVisualProject(page);
    await page.getByRole("button", { name: "新增 Task" }).click();
    await expect(page.getByRole("dialog", { name: "新增 Task" })).toBeVisible();
    await expect(page.getByText("來源")).toHaveCount(0);
    await expect(page).toHaveScreenshot("create-task-dialog.png", {
      fullPage: true,
    });
  });

  test("attention page baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);
    await page.getByRole("button", { name: "展開主選單" }).click();
    await page.getByRole("button", { name: "待處理" }).click();

    await expect(page.getByRole("heading", { name: "待處理" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "優先處理" })).toBeVisible();
    await expect(page.getByRole("button", { name: "審查材料" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "查看缺口" })).toHaveCount(0);
    await expect(page.locator(".attention-task").first().getByRole("button", { name: "Chat", exact: true })).toBeVisible();
    await expect(page.getByText("來源", { exact: true })).toHaveCount(0);
    await expect(page.locator(".attention-task").getByText("Prototype", { exact: true })).toHaveCount(0);
    await expect(page).toHaveScreenshot("attention-page.png", {
      fullPage: true,
    });
  });

  test("full page detail baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await openVisualTaskWorkbench(page);
    await page.getByRole("button", { name: /Task detail 顯示審查附件與人工核准/ }).click();
    await page.getByRole("button", { name: "在完整頁開啟" }).click();
    await expect(page.getByRole("heading", { name: /Task detail 顯示審查附件與人工核准/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "回到 Task 管理" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "審查結論" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Reject" })).toBeVisible();
    await expect(page.locator(".task-page-meta").getByText("Review", { exact: true })).toHaveCount(0);
    await expect(page).toHaveScreenshot("task-detail-full-page.png", {
      fullPage: true,
    });
  });
});
