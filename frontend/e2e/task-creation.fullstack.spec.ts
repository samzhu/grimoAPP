import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

type WorkflowSummaryBody = {
  currentStep: string | null;
  qualityScore: number | null;
};

type TaskBody = {
  id: string;
  projectId: string;
  title: string;
  body: string;
  state: string;
  labels: string[];
  workflowSummary: WorkflowSummaryBody;
};

async function openProjectCreate(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案", exact: true }).click();
  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await page.getByRole("button", { name: "新增專案" }).click();
  await expect(page.getByRole("heading", { name: "新增專案", level: 1 })).toBeVisible();
}

test("AC-S004-4/5 creates a Project-owned BACKLOG Task through real full-stack API wiring", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const projectName = `S004 task project ${suffix}`;
  const taskTitle = `S004 full-stack Task ${suffix}`;
  const taskBody = `S004 browser-created Task ${suffix}`;
  const projectPath = join(tmpdir(), `grimo-s004-task-${suffix}`);
  await mkdir(projectPath, { recursive: true });

  await openProjectCreate(page);
  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S004 full-stack Task creation");
  await page.getByLabel("專案路徑").fill(projectPath);
  await page.getByLabel("專案工作流").selectOption("web-service-development");

  const projectCreateResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await page.getByRole("button", { name: "建立專案" }).click();
  const createdProject = (await (await projectCreateResponsePromise).json()) as {
    id: string;
  };
  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
  await expect(page.getByRole("button", { name: "新增 Task" })).toBeEnabled();
  await page.getByRole("button", { name: "新增 Task" }).click();
  await expect(page.getByRole("dialog", { name: "新增 Task" })).toBeVisible();
  await page.getByLabel("標題").fill(taskTitle);
  await page.getByLabel("任務內容").fill(taskBody);
  await page.getByLabel("Labels").fill("frontend, backend");

  const taskCreateRequestPromise = page.waitForRequest(
    (request) =>
      request.method() === "POST" &&
      request.url().endsWith(`/api/projects/${createdProject.id}/tasks`),
  );
  const taskCreateResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().endsWith(`/api/projects/${createdProject.id}/tasks`),
  );
  await page.getByRole("button", { name: "建立 Task" }).click();

  const createRequestBody = JSON.parse((await taskCreateRequestPromise).postData() ?? "{}") as {
    body: string;
    labels: string[];
    title: string;
  };
  expect(createRequestBody).toEqual({
    title: taskTitle,
    body: taskBody,
    labels: ["frontend", "backend"],
  });
  expect(createRequestBody).not.toHaveProperty("state");
  expect(createRequestBody).not.toHaveProperty("workflowSummary");
  expect(createRequestBody).not.toHaveProperty("skill");

  const taskCreateResponse = await taskCreateResponsePromise;
  expect(taskCreateResponse.ok()).toBe(true);
  const createdTask = (await taskCreateResponse.json()) as TaskBody;
  expect(createdTask).toEqual(
    expect.objectContaining({
      projectId: createdProject.id,
      title: taskTitle,
      body: taskBody,
      state: "BACKLOG",
      labels: ["frontend", "backend"],
      workflowSummary: {
        currentStep: null,
        qualityScore: null,
      },
    }),
  );

  await expect(page.getByRole("dialog", { name: "新增 Task" })).toHaveCount(0);
  await expect(
    page.locator(".board-column", { hasText: "BACKLOG" }).getByText(taskTitle),
  ).toBeVisible();

  const listResponse = await page.request.get(`/api/projects/${createdProject.id}/tasks`);
  expect(listResponse.ok()).toBe(true);
  const taskList = (await listResponse.json()) as { content: TaskBody[] };
  const listedTask = taskList.content.find((task) => task.id === createdTask.id);
  expect(listedTask).toEqual(
    expect.objectContaining({
      id: createdTask.id,
      projectId: createdProject.id,
      title: taskTitle,
      state: "BACKLOG",
      labels: ["frontend", "backend"],
      workflowSummary: {
        currentStep: null,
        qualityScore: null,
      },
    }),
  );
});
