import { expect, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

test("AC-S001-4/5 AC-S002 creates Project with workspace, workflow roles, collection responses, and TSID", async ({
  page,
}) => {
  const suffix = Date.now().toString();
  const projectName = `S002 專案 ${suffix}`;
  const parentPath = join(tmpdir(), `grimo-s002-${suffix}`);
  const workspacePath = join(parentPath, "workspace");

  await mkdir(workspacePath, { recursive: true });

  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案" }).click();

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await page.getByRole("button", { name: "建立專案" }).click();

  const submitButton = page.getByRole("button", { name: "建立專案" });
  await expect(submitButton).toBeDisabled();
  await expect(page.getByLabel("專案工作流")).toContainText("Web 服務開發");

  await page.getByLabel("專案工作流").selectOption("research");
  await expect(page.getByText("這個工作流尚未定義角色")).toBeVisible();
  await expect(page.getByText("Product Manager")).not.toBeVisible();

  await page.getByLabel("專案工作流").selectOption("web-service-development");
  await expect(page.getByText("Product Manager")).toBeVisible();
  await expect(page.getByText("Frontend Engineer")).toBeVisible();
  await expect(page.getByText("Backend Engineer")).toBeVisible();
  await expect(page.getByText("AI Review")).toBeVisible();
  await expect(page.getByText("Human Review")).toBeVisible();
  await expect(page.getByText("optional")).not.toBeVisible();
  await expect(page.getByText("可跳過")).not.toBeVisible();

  await page.getByLabel("專案名稱").fill(projectName);
  await page.getByLabel("專案描述").fill("S002 full-stack 驗收專案");
  await page.getByLabel("專案工作區").fill(parentPath);
  await page.getByRole("button", { name: "選擇資料夾" }).click();
  await page.getByRole("button", { name: "workspace" }).click();
  await page.getByRole("button", { name: "選取此資料夾" }).click();
  await expect(page.getByLabel("專案工作區")).toHaveValue(workspacePath);

  const createRequestPromise = page.waitForRequest(
    (request) => request.method() === "POST" && request.url().endsWith("/api/projects"),
  );
  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().endsWith("/api/projects"),
  );
  await submitButton.click();
  const createRequest = await createRequestPromise;
  const createRequestBody = JSON.parse(createRequest.postData() ?? "{}") as {
    workspacePath?: string;
    folderPath?: string;
  };
  expect(createRequestBody.workspacePath).toBe(workspacePath);
  expect(createRequestBody.folderPath).toBeUndefined();

  const createResponse = await createResponsePromise;
  expect(createResponse.ok()).toBe(true);
  const createdProject = (await createResponse.json()) as {
    id: string;
    workspacePath: string;
    folderPath?: string;
  };
  expect(createdProject.id).toMatch(/^[0-9A-HJKMNP-TV-Z]{13}$/);
  expect(createdProject.workspacePath).toBe(workspacePath);
  expect(createdProject.folderPath).toBeUndefined();

  await expect(page.getByText(`${projectName} 已建立並設為目前專案`)).toBeVisible();
  await expect(page.locator(".project-context")).toContainText(projectName);
  await expect(page.getByRole("button", { name: new RegExp(projectName) })).toBeVisible();

  const response = await page.request.get("/api/projects");
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as {
    content: Array<{
      id: string;
      name: string;
      workspacePath: string;
      workflowRecipeId: string;
      workflowRoles: Array<{ id: string; name: string }>;
    }>;
  };
  const projects = body.content;
  expect(projects).toContainEqual(
    expect.objectContaining({
      id: expect.stringMatching(/^[0-9A-HJKMNP-TV-Z]{13}$/),
      name: projectName,
      workspacePath,
      workflowRecipeId: "web-service-development",
      workflowRoles: expect.arrayContaining([
        expect.objectContaining({ id: "product-manager", name: "Product Manager" }),
        expect.objectContaining({ id: "backend-engineer", name: "Backend Engineer" }),
      ]),
    }),
  );
});
