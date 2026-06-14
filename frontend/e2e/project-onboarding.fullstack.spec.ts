import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { join, resolve } from "node:path";
import { tmpdir } from "node:os";

async function openProjectCreate(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案", exact: true }).click();
  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await page.getByRole("button", { name: "新增專案" }).click();
  await expect(page.getByRole("heading", { name: "新增專案", level: 1 })).toBeVisible();
}

const forbiddenProjectPathFields = [
  "workspacePath",
  "folderPath",
  "projectPathSource",
  "browserProjectPathKey",
  "FileSystemDirectoryHandle",
  "nativeFolderDialog",
] as const;

function expectProjectPathOnlyContract(body: Record<string, unknown>, projectPath: string) {
  expect(body.projectPath).toBe(projectPath);
  for (const field of forbiddenProjectPathFields) {
    expect(body).not.toHaveProperty(field);
  }
}

test("AC-S003-1/2/7 shows list-first Project management and projectPath-only create form", async ({
  page,
}) => {
  const localDirectoryRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/local-directories")) {
      localDirectoryRequests.push(request.url());
    }
  });

  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案", exact: true }).click();

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.getByText("Project list")).toBeVisible();
  await expect(page.getByRole("button", { name: "新增專案" })).toBeVisible();
  await expect(page.getByLabel("專案名稱")).toHaveCount(0);
  await expect(page.getByLabel("專案路徑")).toHaveCount(0);

  await page.getByRole("button", { name: "新增專案" }).click();
  await expect(page.getByRole("heading", { name: "新增專案", level: 1 })).toBeVisible();
  await expect(page.getByRole("button", { name: "返回列表" })).toBeVisible();
  await expect(page.getByLabel("專案路徑")).toBeVisible();
  await expect(page.getByText("未填會使用 Grimo 預設路徑")).toBeVisible();
  await expect(page.getByRole("button", { name: "選擇資料夾" })).toBeVisible();
  await expect(page.locator(".directory-browser")).toHaveCount(0);
  expect(localDirectoryRequests).toHaveLength(0);

  await page.getByRole("button", { name: "返回列表" }).click();
  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.getByText("Project list")).toBeVisible();
});

test("AC-S003-3 creates Project with generated projectPath when projectPath is blank", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const projectName = `S003 generated ${suffix}`;
  await openProjectCreate(page);

  const submitButton = page.getByRole("button", { name: "建立專案" });
  await expect(submitButton).toBeDisabled();
  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S003 full-stack generated path");
  await page.getByLabel("專案工作流").selectOption("web-service-development");
  await expect(submitButton).toBeEnabled();

  const createRequestPromise = page.waitForRequest(
    (request) => request.method() === "POST" && request.url().endsWith("/api/projects"),
  );
  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await submitButton.click();

  const createRequestBody = JSON.parse((await createRequestPromise).postData() ?? "{}") as {
    projectPath?: string;
    workspacePath?: string;
    projectPathSource?: string;
    browserProjectPathKey?: string;
  };
  expect(createRequestBody.projectPath).toBeUndefined();
  expect(createRequestBody.workspacePath).toBeUndefined();
  expect(createRequestBody.projectPathSource).toBeUndefined();
  expect(createRequestBody.browserProjectPathKey).toBeUndefined();

  const createdProject = (await (await createResponsePromise).json()) as {
    id: string;
    projectPath: string;
    workspacePath?: string;
    projectPathSource?: string;
    backendPathReady?: boolean;
    projectDataPath?: string;
  };
  expect(createdProject.id).toMatch(/^[0-9A-HJKMNP-TV-Z]{13}$/);
  expect(createdProject.projectPath).toMatch(/\/\.grimo\/projects\/[0-9A-HJKMNP-TV-Z]{13}$/);
  expect(createdProject.workspacePath).toBeUndefined();
  expect(createdProject.projectPathSource).toBeUndefined();
  expect(createdProject.backendPathReady).toBeUndefined();
  expect(createdProject.projectDataPath).toBeUndefined();

  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
  await expect(page.locator(".project-context")).toContainText(projectName);
  await expect(page.getByRole("button", { name: new RegExp(projectName) })).toContainText(
    createdProject.projectPath,
  );
});

test("AC-S003-4 creates Project with validated manual projectPath", async ({ page }) => {
  const suffix = Date.now().toString();
  const projectName = `S003 manual ${suffix}`;
  const projectPath = join(tmpdir(), `grimo-s003-manual-${suffix}`);
  await mkdir(projectPath, { recursive: true });
  await openProjectCreate(page);

  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S003 full-stack manual path");
  await page.getByLabel("專案路徑").fill(projectPath);
  await page.getByLabel("專案工作流").selectOption("web-service-development");
  await expect(page.getByText("Product Manager")).toBeVisible();
  await expect(page.getByText("Frontend Engineer")).toBeVisible();
  await expect(page.getByText("Backend Engineer")).toBeVisible();
  await expect(page.getByText("Unit-test", { exact: true })).toBeVisible();
  await expect(page.getByText("Integration-test", { exact: true })).toBeVisible();
  await expect(page.getByText("E2E-test", { exact: true })).toBeVisible();
  await expect(page.getByText("release", { exact: true })).toBeVisible();

  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await page.getByRole("button", { name: "建立專案" }).click();
  const createdProject = (await (await createResponsePromise).json()) as {
    id: string;
    projectPath: string;
    workspacePath?: string;
  };
  expect(createdProject.projectPath).toBe(projectPath);
  expect(createdProject.workspacePath).toBeUndefined();

  const response = await page.request.get("/api/projects");
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as {
    content: Array<{
      id: string;
      name: string;
      projectPath: string;
      workspacePath?: string;
      workflowRecipeId: string;
      workflowRoles: Array<{ id: string; name: string }>;
    }>;
  };
  const listedProject = body.content.find((project) => project.name === projectName);
  expect(listedProject).toEqual(
    expect.objectContaining({
      id: expect.stringMatching(/^[0-9A-HJKMNP-TV-Z]{13}$/),
      projectPath,
      workflowRecipeId: "web-service-development",
      workflowRoles: expect.arrayContaining([
        expect.objectContaining({ id: "product-manager", name: "Product Manager" }),
        expect.objectContaining({ id: "backend-engineer", name: "Backend Engineer" }),
      ]),
    }),
  );
  expect(listedProject).not.toHaveProperty("workspacePath");
});

test("AC-S014-2/3/7/9: creates Project from existing folder browser selected projectPath only", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const projectName = `S014 existing ${suffix}`;
  const existingFolderName = `repo-existing-${suffix}`;
  const fullstackHome = resolve("../temp/grimo-fullstack-home");
  const projectPath = join(fullstackHome, ".grimo", "projects", existingFolderName);
  await mkdir(projectPath, { recursive: true });
  const nativeDialogRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/native-folder-dialogs/project-path")) {
      nativeDialogRequests.push(request.url());
    }
  });

  await openProjectCreate(page);
  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S014 full-stack existing folder browser path");
  await page.getByLabel("專案工作流").selectOption("web-service-development");

  await page.getByRole("button", { name: "選擇資料夾" }).click();
  await expect(page.getByRole("dialog", { name: "選擇 Project 資料夾" })).toBeVisible();
  await expect(page.locator(".folder-current-path")).toHaveText(projectPath.replace(`/${existingFolderName}`, ""));
  await expect(page.getByRole("button", { name: "使用此資料夾" })).toBeDisabled();
  await page.getByRole("button", { name: new RegExp(existingFolderName) }).click();
  await expect(page.locator(".folder-current-path")).toHaveText(projectPath);
  await page.getByRole("button", { name: "使用此資料夾" }).click();
  await expect(page.getByLabel("專案路徑")).toHaveValue(projectPath);

  const createRequestPromise = page.waitForRequest(
    (request) => request.method() === "POST" && request.url().endsWith("/api/projects"),
  );
  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await page.getByRole("button", { name: "建立專案" }).click();

  const createRequestBody = JSON.parse((await createRequestPromise).postData() ?? "{}") as Record<string, unknown>;
  expectProjectPathOnlyContract(createRequestBody, projectPath);

  const createdProject = (await (await createResponsePromise).json()) as {
    id: string;
    name: string;
    projectPath: string;
  } & Record<string, unknown>;
  expect(createdProject.name).toBe(projectName);
  expectProjectPathOnlyContract(createdProject, projectPath);

  const response = await page.request.get("/api/projects");
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as {
    content: Array<{
      name: string;
      projectPath: string;
    } & Record<string, unknown>>;
  };
  const listedProject = body.content.find((project) => project.name === projectName);
  expect(listedProject).toEqual(
    expect.objectContaining({
      projectPath,
    }),
  );
  expectProjectPathOnlyContract(listedProject ?? {}, projectPath);
  expect(nativeDialogRequests).toHaveLength(0);
});

test("AC-S014-5/7/9: creates Project from folder browser created projectPath only", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const projectName = `S014 created ${suffix}`;
  const newFolderName = `repo-created-${suffix}`;
  const fullstackHome = resolve("../temp/grimo-fullstack-home");
  const defaultRoot = join(fullstackHome, ".grimo", "projects");
  const projectPath = join(defaultRoot, newFolderName);
  const nativeDialogRequests: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/native-folder-dialogs/project-path")) {
      nativeDialogRequests.push(request.url());
    }
  });

  await openProjectCreate(page);
  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S014 full-stack created folder browser path");
  await page.getByLabel("專案工作流").selectOption("web-service-development");

  await page.getByRole("button", { name: "選擇資料夾" }).click();
  await expect(page.getByRole("dialog", { name: "選擇 Project 資料夾" })).toBeVisible();
  await expect(page.locator(".folder-current-path")).toHaveText(defaultRoot);
  await page.getByRole("button", { name: "建立新資料夾" }).click();
  await page.getByLabel("資料夾名稱").fill(newFolderName);
  await page.getByRole("button", { name: "建立並使用" }).click();
  await expect(page.getByRole("dialog", { name: "選擇 Project 資料夾" })).toHaveCount(0);
  await expect(page.getByLabel("專案路徑")).toHaveValue(projectPath);

  const createRequestPromise = page.waitForRequest(
    (request) => request.method() === "POST" && request.url().endsWith("/api/projects"),
  );
  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await page.getByRole("button", { name: "建立專案" }).click();

  const createRequestBody = JSON.parse((await createRequestPromise).postData() ?? "{}") as Record<string, unknown>;
  expectProjectPathOnlyContract(createRequestBody, projectPath);

  const createdProject = (await (await createResponsePromise).json()) as {
    id: string;
    name: string;
    projectPath: string;
  } & Record<string, unknown>;
  expect(createdProject.name).toBe(projectName);
  expectProjectPathOnlyContract(createdProject, projectPath);

  const response = await page.request.get("/api/projects");
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as {
    content: Array<{
      name: string;
      projectPath: string;
    } & Record<string, unknown>>;
  };
  const listedProject = body.content.find((project) => project.name === projectName);
  expect(listedProject).toEqual(
    expect.objectContaining({
      projectPath,
    }),
  );
  expectProjectPathOnlyContract(listedProject ?? {}, projectPath);
  expect(nativeDialogRequests).toHaveLength(0);
});

test("AC-S003-5 rejects invalid manual projectPath without adding Project", async ({ page }) => {
  const suffix = Date.now().toString();
  const projectName = `S003 invalid ${suffix}`;
  const missingProjectPath = join(tmpdir(), `grimo-s003-missing-${suffix}`);
  await openProjectCreate(page);

  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S003 full-stack invalid path");
  await page.getByLabel("專案路徑").fill(missingProjectPath);
  await page.getByLabel("專案工作流").selectOption("web-service-development");
  await page.getByRole("button", { name: "建立專案" }).click();

  await expect(page.getByText("請輸入有效的本機資料夾路徑")).toBeVisible();

  const response = await page.request.get("/api/projects");
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as { content: Array<{ name: string }> };
  expect(body.content).not.toContainEqual(expect.objectContaining({ name: projectName }));
});
