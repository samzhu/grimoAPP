import { expect, test } from "@playwright/test";

const viewports = [
  { name: "desktop-1366", width: 1366, height: 768 },
  { name: "desktop-1440", width: 1440, height: 900 },
  { name: "mobile-390", width: 390, height: 844 },
  { name: "tablet-820", width: 820, height: 1180 },
] as const;

test.describe("Task workbench visual gate", () => {
  for (const viewport of viewports) {
    test(`board baseline ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.goto("/");
      await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
      await expect(page.getByRole("button", { name: "新增 Task" })).toBeVisible();
      await expect(page.getByPlaceholder("搜尋任務 / 關鍵字")).toBeVisible();
      await expect(page.locator(".task-card.selected")).toHaveCount(0);
      await expect(page).toHaveScreenshot(`task-workbench-${viewport.name}.png`, {
        fullPage: true,
      });
    });
  }

  test("main navigation overlays until pinned", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");

    await page.getByRole("button", { name: "展開主選單" }).click();
    await expect(page.locator(".workspace-shell.nav-open")).toHaveCount(1);
    await expect(page.locator(".workspace-shell.nav-pinned")).toHaveCount(0);

    await page.getByRole("button", { name: "固定主選單" }).click();
    await expect(page.locator(".workspace-shell.nav-pinned")).toHaveCount(1);
  });

  test("attention focus can collapse and expand", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");

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
    await page.goto("/");

    await expect(page.getByText("補上下文")).toHaveCount(0);
    await page.locator(".focus-task-card").first().getByRole("button", { name: "Chat", exact: true }).click();

    await expect(page.getByRole("heading", { name: "工作形成 Chat" })).toBeVisible();
    await expect(page.getByText("GRM-188", { exact: true })).toBeVisible();
  });

  test("desktop board is viewport bounded", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");

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
    await page.goto("/");
    await page.getByRole("button", { name: /執行前設定與本機能力力檢查|執行前設定與本機能力檢查/ }).click();
    await expect(page.getByRole("heading", { name: "任務詳情" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "執行前設定與本機能力檢查" })).toBeVisible();
    await expect(page.getByText("來源", { exact: true })).toBeVisible();
    await expect(page).toHaveScreenshot("task-detail-drawer.png", {
      fullPage: true,
    });
  });

  test("create task dialog baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await page.getByRole("button", { name: "新增 Task" }).click();
    await expect(page.getByRole("dialog", { name: "新增 Task" })).toBeVisible();
    await expect(page.getByText("來源")).toHaveCount(0);
    await expect(page).toHaveScreenshot("create-task-dialog.png", {
      fullPage: true,
    });
  });

  test("attention page baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await page.getByRole("button", { name: "展開主選單" }).click();
    await page.getByRole("button", { name: "待處理" }).click();

    await expect(page.getByRole("heading", { name: "待處理" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "優先處理" })).toBeVisible();
    await expect(page.getByRole("button", { name: "審查材料" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "查看缺口" })).toHaveCount(0);
    await expect(page.locator(".attention-task").first().getByRole("button", { name: "Chat", exact: true })).toBeVisible();
    await expect(page.getByText("來源", { exact: true })).toHaveCount(0);
    await expect(page).toHaveScreenshot("attention-page.png", {
      fullPage: true,
    });
  });

  test("full page detail baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await page.getByRole("button", { name: /Task detail 顯示審查附件與人工核准/ }).click();
    await page.getByRole("button", { name: "在完整頁開啟" }).click();
    await expect(page.getByRole("heading", { name: /Task detail 顯示審查附件與人工核准/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "回到 Task 管理" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "審查結論" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Reject" })).toBeVisible();
    await expect(page).toHaveScreenshot("task-detail-full-page.png", {
      fullPage: true,
    });
  });
});
