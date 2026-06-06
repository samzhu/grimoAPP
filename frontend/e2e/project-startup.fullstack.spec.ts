import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

type ProjectBody = {
  id: string;
  name: string;
  projectPath: string;
};

async function createProjectViaApi(page: Page, name: string, projectPath: string) {
  await mkdir(projectPath, { recursive: true });
  const response = await page.request.post("/api/projects", {
    data: {
      name,
      description: `${name} full-stack fixture`,
      projectPath,
      workflowRecipeId: "web-service-development",
    },
  });
  expect(response.ok()).toBe(true);
  return (await response.json()) as ProjectBody;
}

test("AC-S010-2: creating Project from startup gate routes to Task Workbench", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const projectName = `S010 startup ${suffix}`;
  const projectPath = join(tmpdir(), `grimo-s010-startup-${suffix}`);
  const taskRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/projects/") && request.url().endsWith("/tasks")) {
      taskRequests.push(request.url());
    }
  });

  await mkdir(projectPath, { recursive: true });
  await page.goto("/");
  await page.getByRole("button", { name: "建立 Project" }).click();
  await expect(page.getByRole("heading", { name: "新增專案", level: 1 })).toBeVisible();

  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S010 startup create");
  await page.getByLabel("專案路徑").fill(projectPath);
  await page.getByLabel("專案工作流").selectOption("web-service-development");
  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await page.getByRole("button", { name: "建立專案" }).click();
  const createdProject = (await (await createResponsePromise).json()) as ProjectBody;

  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
  await expect(page.locator(".project-context")).toContainText(projectName);
  await expect.poll(() => taskRequests).toContainEqual(
    expect.stringContaining(`/api/projects/${createdProject.id}/tasks`),
  );
  await expect
    .poll(() =>
      page.evaluate(() => JSON.parse(window.localStorage.getItem("grimo.projectSession") || "{}")),
    )
    .toEqual({ lastActiveProjectId: createdProject.id, isClosed: false });
});

test("AC-S010-4: selecting Project from startup gate loads that Project tasks", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const grimoProject = await createProjectViaApi(
    page,
    `S010 grimoAPP ${suffix}`,
    join(tmpdir(), `grimo-s010-grimo-${suffix}`),
  );
  const skillsProject = await createProjectViaApi(
    page,
    `S010 skills-hub ${suffix}`,
    join(tmpdir(), `grimo-s010-skills-${suffix}`),
  );
  const taskRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/projects/") && request.url().endsWith("/tasks")) {
      taskRequests.push(request.url());
    }
  });

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "選擇或建立 Project" })).toBeVisible();
  await page.getByRole("button", { name: new RegExp(skillsProject.name) }).click();

  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
  await expect(page.locator(".project-context")).toContainText(skillsProject.name);
  await expect.poll(() => taskRequests).toContainEqual(
    expect.stringContaining(`/api/projects/${skillsProject.id}/tasks`),
  );
  expect(taskRequests.some((url) => url.includes(grimoProject.id))).toBe(false);
});
