import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const selectedProject = {
  id: "01HZTASKPROJECT",
  name: "grimoAPP",
  description: "本機 AI 開發工作台",
  projectPath: "/tmp/grimo-s004-project",
  workflowRecipeId: "web-service-development",
  workflowRecipeName: "Web 服務開發",
  status: "ACTIVE",
  createdAt: "2026-06-04T00:00:00Z",
  updatedAt: "2026-06-04T00:00:00Z",
  workflowRoles: [],
};

const createdTask = {
  id: "01HZTASKCARD01",
  projectId: selectedProject.id,
  title: "接上 Task API",
  body: "從前端送出 manual Task",
  description: "從前端送出 manual Task",
  state: "BACKLOG",
  source: "manual",
  workflowRecipeId: "web-service-development",
  workflowSummary: {
    currentStep: null,
    qualityScore: null,
  },
  createdAt: "2026-06-04T00:01:00Z",
  updatedAt: "2026-06-04T00:01:00Z",
  acceptance: [],
  gaps: [],
  evidence: [],
  labels: ["frontend", "backend"],
  commentCount: 0,
};

async function mockTaskApis(page: Page) {
  const createBodies: unknown[] = [];
  let taskApiRequests = 0;
  const taskList = { content: [] };

  await page.route(`**/api/projects/${selectedProject.id}/tasks`, async (route) => {
    taskApiRequests += 1;
    if (route.request().method() === "POST") {
      createBodies.push(route.request().postDataJSON());
      await route.fulfill({ json: createdTask });
      return;
    }
    await route.fulfill({ json: taskList });
  });
  await page.route("**/api/projects", async (route) => {
    await route.fulfill({ json: { content: [selectedProject] } });
  });

  return {
    createBodies,
    getTaskApiRequests: () => taskApiRequests,
  };
}

async function openSelectedProjectTasks(page: Page) {
  await page.addInitScript((projectId) => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: projectId, isClosed: false }),
    );
  }, selectedProject.id);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
}

test("AC-S004-4: selected Project can create a Task through the backend API shape", async ({ page }) => {
  const api = await mockTaskApis(page);
  await openSelectedProjectTasks(page);

  await test.step("When the user opens Create Task", async () => {
    await page.getByRole("button", { name: "新增 Task" }).click();
  });

  await test.step("Then the dialog exposes Task fields without skill or draft wording", async () => {
    await expect(page.getByRole("dialog", { name: "新增 Task" })).toBeVisible();
    await expect(page.getByLabel("標題")).toBeVisible();
    await expect(page.getByLabel("任務內容")).toBeVisible();
    await expect(page.getByLabel("Labels")).toBeVisible();
    await expect(page.getByLabel("建議 skill")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "建立草稿" })).toHaveCount(0);
  });

  await test.step("When the user submits a new Task", async () => {
    await page.getByLabel("標題").fill(createdTask.title);
    await page.getByLabel("任務內容").fill(createdTask.body);
    await page.getByLabel("Labels").fill("frontend, backend");
    await page.getByRole("button", { name: "建立 Task" }).click();
  });

  await test.step("Then only user-owned Task fields are posted", async () => {
    expect(api.createBodies).toHaveLength(1);
    expect(Object.keys(api.createBodies[0] as Record<string, unknown>).sort()).toEqual([
      "body",
      "labels",
      "title",
    ]);
    expect(api.createBodies[0]).toEqual({
      title: createdTask.title,
      body: createdTask.body,
      labels: ["frontend", "backend"],
    });
  });

  await test.step("And the returned BACKLOG card appears", async () => {
    await expect(page.getByRole("dialog", { name: "新增 Task" })).toHaveCount(0);
    await expect(page.locator(".board-column", { hasText: "BACKLOG" }).getByText(createdTask.title)).toBeVisible();
  });
});

test("AC-S004-4: without a current Project the Task board cannot create an orphan Task", async ({ page }) => {
  let taskApiRequests = 0;
  await page.route("**/api/projects", async (route) => {
    await route.fulfill({ json: { content: [] } });
  });
  await page.route("**/api/projects/*/tasks", async (route) => {
    taskApiRequests += 1;
    await route.fulfill({ json: { content: [] } });
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "建立第一個 Project" })).toBeVisible();
  await expect(page.getByRole("button", { name: "新增 Task" })).toHaveCount(0);
  expect(taskApiRequests).toBe(0);
});
