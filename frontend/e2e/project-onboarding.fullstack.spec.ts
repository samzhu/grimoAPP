import { expect, test } from "@playwright/test";

test("AC-S001-4/5 creates Project through Vite proxy and backend API", async ({ page }) => {
  const suffix = Date.now().toString();
  const projectName = `S001 專案 ${suffix}`;
  const folderPath = `/tmp/grimo-s001-${suffix}`;

  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案" }).click();

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.getByLabel("專案工作流")).toContainText("開發工作流");

  await page.getByLabel("名稱").fill(projectName);
  await page.getByLabel("描述").fill("S001 full-stack 驗收專案");
  await page.getByLabel("專案資料夾").fill(folderPath);
  await page.getByLabel("專案工作流").selectOption("coding");
  await page.getByRole("button", { name: "新增專案" }).click();

  await expect(page.getByText(`${projectName} 已新增並設為目前專案`)).toBeVisible();
  await expect(page.locator(".project-context")).toContainText(projectName);
  await expect(page.getByRole("button", { name: new RegExp(projectName) })).toBeVisible();

  const response = await page.request.get("/api/projects");
  expect(response.ok()).toBe(true);
  const projects = (await response.json()) as Array<{ name: string; folderPath: string }>;
  expect(projects).toContainEqual(expect.objectContaining({ name: projectName, folderPath }));
});
