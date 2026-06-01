import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const workflowRecipes = {
  content: [
    {
      id: "web-service-development",
      name: "Web 服務開發",
      description: "從需求到 release 的 Web 服務開發工作流",
      category: "coding",
      steps: [
        { id: "discuss", name: "Discuss", taskState: "DEFINING" },
        { id: "dev", name: "Dev", taskState: "RUNNING" },
      ],
      roles: [
        {
          id: "product-manager",
          name: "Product Manager",
          description: "釐清產品目標與 acceptance。",
          primarySteps: ["Discuss"],
        },
      ],
      qualityLoopSummary: "Review -> Rating -> Fix until quality_score > 9",
    },
  ],
};

const createdProject = {
  id: "01HZ7Y2P8M9QK",
  name: "grimoAPP",
  description: "本機 AI 開發工作台",
  projectPath: "/tmp/grimo-s003-repo",
  workflowRecipeId: "web-service-development",
  workflowRecipeName: "Web 服務開發",
  status: "ACTIVE",
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-01T00:00:00Z",
  workflowRoles: [],
};

async function mockProjectApis(page: Page) {
  const createBodies: unknown[] = [];
  let localDirectoryRequests = 0;

  await page.route("**/api/projects", async (route) => {
    if (route.request().method() === "POST") {
      createBodies.push(route.request().postDataJSON());
      await route.fulfill({ json: createdProject });
      return;
    }
    await route.fulfill({ json: { content: [] } });
  });
  await page.route("**/api/workflow-recipes", async (route) => {
    await route.fulfill({ json: workflowRecipes });
  });
  await page.route("**/api/local-directories**", async (route) => {
    localDirectoryRequests += 1;
    await route.fulfill({ json: { path: "/tmp", parentPath: null, directories: [] } });
  });

  return {
    createBodies,
    getLocalDirectoryRequests: () => localDirectoryRequests,
  };
}

async function openProjectsView(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案" }).click();
}

test("AC-S003-1: list view hides Project create fields before user starts creation", async ({ page }) => {
  await mockProjectApis(page);

  await test.step("Given the user opens Project management", async () => {
    await openProjectsView(page);
  });

  await test.step("Then the list view is the first screen", async () => {
    await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
    await expect(page.getByText("Project list")).toBeVisible();
    await expect(page.getByText("尚未建立專案")).toBeVisible();
    await expect(page.getByRole("button", { name: "新增專案" })).toBeVisible();
    await expect(page.getByLabel("專案名稱")).toHaveCount(0);
    await expect(page.getByLabel("專案描述")).toHaveCount(0);
    await expect(page.getByLabel("專案路徑")).toHaveCount(0);
  });
});

test("AC-S003-2: create view can return to Project list without clearing loaded projects", async ({ page }) => {
  await mockProjectApis(page);
  await openProjectsView(page);

  await test.step('When the user clicks "新增專案"', async () => {
    await page.getByRole("button", { name: "新增專案" }).click();
  });

  await test.step("Then the Project Creation Page is visible", async () => {
    await expect(page.getByRole("heading", { name: "新增專案", level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: "返回列表" })).toBeVisible();
    await expect(page.getByLabel("專案名稱")).toBeVisible();
    await expect(page.getByLabel("專案描述")).toBeVisible();
    await expect(page.getByLabel("專案路徑")).toBeVisible();
    await expect(page.getByLabel("專案工作流")).toBeVisible();
  });

  await test.step("And returning keeps the loaded list state", async () => {
    await page.getByRole("button", { name: "返回列表" }).click();
    await expect(page.getByText("Project list")).toBeVisible();
    await expect(page.getByText("尚未建立專案")).toBeVisible();
  });
});

test("AC-S003-7: create view submits projectPath only and does not render directory browser", async ({ page }) => {
  const api = await mockProjectApis(page);
  await openProjectsView(page);
  await page.getByRole("button", { name: "新增專案" }).click();

  await test.step("Then the projectPath field stays a text input", async () => {
    await expect(page.getByLabel("專案路徑")).toBeVisible();
    await expect(page.getByText("未填會使用 Grimo 預設路徑")).toBeVisible();
    await expect(page.getByRole("button", { name: "選擇資料夾" })).toHaveCount(0);
    await expect(page.getByText("上層")).toHaveCount(0);
    await expect(page.getByText("選取此資料夾")).toHaveCount(0);
    await expect(page.locator(".directory-browser")).toHaveCount(0);
  });

  await test.step("When the user submits a Project", async () => {
    await page.getByLabel("專案名稱").fill("grimoAPP");
    await page.getByLabel("專案描述").fill("本機 AI 開發工作台");
    await page.getByLabel("專案路徑").fill("/tmp/grimo-s003-repo");
    await page.getByLabel("專案工作流").selectOption("web-service-development");
    await page.getByRole("button", { name: "建立專案" }).click();
  });

  await test.step("Then the request body contains projectPath only", async () => {
    expect(api.getLocalDirectoryRequests()).toBe(0);
    expect(api.createBodies).toHaveLength(1);
    expect(api.createBodies[0]).toMatchObject({
      name: "grimoAPP",
      description: "本機 AI 開發工作台",
      projectPath: "/tmp/grimo-s003-repo",
      workflowRecipeId: "web-service-development",
    });
    expect(api.createBodies[0]).not.toHaveProperty("workspacePath");
    expect(api.createBodies[0]).not.toHaveProperty("projectPathSource");
    expect(api.createBodies[0]).not.toHaveProperty("browserProjectPathKey");
  });
});
