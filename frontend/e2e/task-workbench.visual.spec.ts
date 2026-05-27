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
      await expect(page).toHaveScreenshot(`task-workbench-${viewport.name}.png`, {
        fullPage: true,
      });
    });
  }

  test("selected task opens detail drawer", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await page.getByRole("button", { name: /執行前設定與本機能力力檢查|執行前設定與本機能力檢查/ }).click();
    await expect(page.getByRole("heading", { name: "任務詳情" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "執行前設定與本機能力檢查" })).toBeVisible();
    await expect(page).toHaveScreenshot("task-detail-drawer.png", {
      fullPage: true,
    });
  });

  test("create task dialog baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await page.getByRole("button", { name: "新增 Task" }).click();
    await expect(page.getByRole("dialog", { name: "新增 Task" })).toBeVisible();
    await expect(page).toHaveScreenshot("create-task-dialog.png", {
      fullPage: true,
    });
  });

  test("full page detail baseline", async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/");
    await page.getByRole("button", { name: /Task detail 顯示審查附件與人工核准/ }).click();
    await page.getByRole("button", { name: "在完整頁開啟" }).click();
    await expect(page.getByRole("heading", { name: /Task detail 顯示審查附件與人工核准/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "← 回到 Task 管理" })).toBeVisible();
    await expect(page).toHaveScreenshot("task-detail-full-page.png", {
      fullPage: true,
    });
  });
});
